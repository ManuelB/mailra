/*
 * Copyright (C) 2008 Markus KARG (markus-karg@users.sourceforge.net)
 * 
 * This file is part of Mail Resource Adapter.
 * 
 * Mail Resource Adapter is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * Mail Resource Adapter is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * Mail Resource Adapter; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package net.sourceforge.mailra.spi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Collection;
import java.util.LinkedList;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.resource.spi.UnavailableException;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.Work;

import net.sourceforge.mailra.api.inbound.MessageListener;

public final class HandleCallerWork implements Work {

	private static final Logger LOGGER = Logger.getLogger(HandleCallerWork.class.getName());

	private final Socket socket;

	private final Collection<MessageEndpointFactory> messageEndpointFactories;

	HandleCallerWork(final Socket socket, final Collection<MessageEndpointFactory> messageEndpointFactories) {
		this.socket = socket;
		this.messageEndpointFactories = messageEndpointFactories;
	}

	@Override
	public final void run() {
		try {
			final InputStream is = this.socket.getInputStream();
			try {
				final BufferedReader input = new BufferedReader(new InputStreamReader(is));
				try {
					final PrintWriter output = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream()));
					try {
						output.println("220 MailRA Service ready");
						output.flush();

						didNotQuit = true;
						while (didNotQuit) {
							final String answeredLine;

							if (this.isDataMode)
								answeredLine = this.handleData(is);
							else {
								final String receivedLine = input.readLine();

								HandleCallerWork.LOGGER.finest(String.format("R: %s", receivedLine));

								answeredLine = this.handleLine(receivedLine);
							}

							if (answeredLine != null) {
								output.println(answeredLine);
								output.flush();

								HandleCallerWork.LOGGER.finest(String.format("S: %s", answeredLine));
							}
						}
					} finally {
						output.close();
					}
				} finally {
					input.close();
				}
			} finally {
				is.close();
			}
		} catch (final IOException e) {
			/*
			 * If there are any communication problems, we just drop the call.
			 * If the caller is programmed in a stable manner, it will call
			 * again.
			 */
		}

		try {
			this.socket.close();
		} catch (final IOException e) {
			/*
			 * Seems TCP is completely screwed. We just give up.
			 */
		}
	}

	private boolean didNotQuit;

	private boolean isDataMode = false;

	private String sender;

	private final Collection<String> recipients = new LinkedList<String>();

	private StringBuilder data = new StringBuilder();

	private final String handleLine(final String line) {
		return this.handleCommandVerb(line);
	}

	private final String handleData(final InputStream is) {
		try {
			final Message message = new MimeMessage((Session) null, new StopTokenInputStream(is, "\r\n.\r\n".getBytes()));
			this.isDataMode = false;

			try {
				this.triggerMessageEndpoint(message);
			} catch (final UnavailableException e) {
				HandleCallerWork.LOGGER.info(e.toString());
			}

			HandleCallerWork.LOGGER.fine("Received mail.");

			this.clear();

			return this.answerOK();
		} catch (final MessagingException e) {
			return this.answerSyntaxErrorInParametersOrArguments();
		}
	}

	private final void triggerMessageEndpoint(final Message message) throws UnavailableException {
		/*
		 * TODO It makes no sense to accept SMTP mails if there is no endpoint
		 * factory.
		 */

		final MessageEndpoint messageEndpoint = this.messageEndpointFactories.iterator().next().createEndpoint(null);
		try {
			final MessageListener messageListener = (MessageListener) messageEndpoint;
			messageListener.onMessage(message);
		} finally {
			messageEndpoint.release();
		}
	}

	private final String handleCommandVerb(final String line) {
		final String smtpCommandVerb = line.substring(0, 4).toUpperCase();

		if (smtpCommandVerb.equals("HELO"))
			return this.handleHELO();
		else if (smtpCommandVerb.equals("EHLO"))
			return this.handleEHLO();
		else if (smtpCommandVerb.equals("MAIL"))
			return this.handleMAIL(line);
		else if (smtpCommandVerb.equals("RCPT"))
			return this.handleRCPT(line);
		else if (smtpCommandVerb.equals("DATA"))
			return this.handleDATA();
		else if (smtpCommandVerb.equals("RSET"))
			return this.handleRSET();
		else if (smtpCommandVerb.equals("QUIT"))
			return this.handleQUIT();
		else if (smtpCommandVerb.equals("NOOP"))
			return this.handleNOOP();
		else
			return this.answerUnknownCommand();
	}

	private final String handleHELO() {
		return this.answerOK();
	}

	private final String handleEHLO() {
		return this.answerOK();
	}

	private final String handleQUIT() {
		this.clear();
		this.didNotQuit = false;
		return "221 MailRA Service closing transmission channel";
	}

	private final String handleRCPT(final String line) {
		final Matcher matcher = Pattern.compile("RCPT TO:([^ ]*)(?: ([^ ]*))?").matcher(line);
		if (matcher.find()) {
			if (matcher.groupCount() > 0) {
				final String forwardPath = matcher.group(1);

				/*
				 * Rcpt Parameters are not supported by this version of MailRA.
				 * 
				 * final String rcptParameters = matcher.group(2);
				 */

				this.recipients.add(forwardPath);
				return this.answerOK();
			}
		}

		return this.answerSyntaxErrorInParametersOrArguments();
	}

	private final String handleDATA() {
		this.isDataMode = true;
		return "354 Start mail input; end with <CRLF>.<CRLF>";
	}

	private final String handleMAIL(final String line) {
		final Matcher matcher = Pattern.compile("MAIL FROM:([^ ]*)(?: ([^ ]*))?").matcher(line);
		if (matcher.find()) {
			if (matcher.groupCount() > 0) {
				final String reversePath = matcher.group(1);

				/*
				 * Mail Parameters are not supported by this version of MailRA.
				 * 
				 * final String mailParameters = matcher.group(2);
				 */

				this.sender = reversePath;
				return this.answerOK();
			}
		}

		return this.answerSyntaxErrorInParametersOrArguments();
	}

	private final String handleRSET() {
		this.clear();
		return this.answerOK();
	}

	private final String handleNOOP() {
		return this.answerOK();
	}

	private final String answerOK() {
		return "250 OK";
	}

	private final String answerUnknownCommand() {
		return "500 Unknown command";
	}

	private final String answerSyntaxErrorInParametersOrArguments() {
		return "501 Syntax error in parameters or arguments";
	}

	private final void clear() {
		this.sender = null;
		this.recipients.clear();
		this.data.delete(0, this.data.length());
	}

	@Override
	public final void release() {
		/*
		 * Handling a call is not a long work. It is acceptable that the
		 * application server has to create another thread instead of reusing
		 * ours. So we reject the application server's appeal to shutdown our
		 * work by just doing nothing here.
		 */
	}

}