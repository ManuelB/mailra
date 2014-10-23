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
import java.util.logging.Logger;

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

	private static final Logger LOGGER = Logger
			.getLogger(HandleCallerWork.class.getName());

	private final Socket socket;

	private final Collection<MessageEndpointFactory> messageEndpointFactories;

	private final int timeout;

	HandleCallerWork(final Socket socket,
			final Collection<MessageEndpointFactory> messageEndpointFactories,
			final int timeout) {
		this.socket = socket;
		this.messageEndpointFactories = messageEndpointFactories;
		this.timeout = timeout;
	}

	@Override
	public final void run() {
		try {
			this.socket.setKeepAlive(true);
			this.socket.setSoTimeout(this.timeout);

			final InputStream is = this.socket.getInputStream();
			try {
				final BufferedReader input = new BufferedReader(
						new InputStreamReader(is));
				try {
					final PrintWriter output = new PrintWriter(
							new OutputStreamWriter(
									this.socket.getOutputStream()));
					try {
						output.println("220 MailRA Service ready");
						output.flush();

						while (this.didNotQuit)
							this.handleCommandVerb(input.readLine(), is, output);
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
			HandleCallerWork.LOGGER.warning(String.format("IOException: ", e));
		}

		try {
			this.socket.close();
		} catch (final IOException e) {
			/*
			 * Seems TCP is completely screwed. We just give up.
			 */
			HandleCallerWork.LOGGER.warning(String.format("IOException: ", e));
		}
	}

	private boolean didNotQuit = true;

	private final void handleData(final InputStream is)
			throws MessagingException {
		final Message message = new MimeMessage((Session) null,
				new StopTokenInputStream(is, "\r\n.\r\n".getBytes()));

		try {
			this.triggerMessageEndpoint(message);
		} catch (final UnavailableException e) {
			HandleCallerWork.LOGGER.info(e.toString());
		}

		HandleCallerWork.LOGGER.fine("Received mail.");
	}

	private final void triggerMessageEndpoint(final Message message)
			throws UnavailableException {
		/*
		 * TODO It makes no sense to accept SMTP mails if there is no endpoint
		 * factory.
		 */

		final MessageEndpoint messageEndpoint = this.messageEndpointFactories
				.iterator().next().createEndpoint(null);
		try {
			final MessageListener messageListener = (MessageListener) messageEndpoint;
			messageListener.onMessage(message);
		} finally {
			messageEndpoint.release();
		}
	}

	private final void handleCommandVerb(final String line,
			final InputStream is, final PrintWriter output) {
		final String smtpCommandVerb = line.substring(0, 4).toUpperCase();

		if (smtpCommandVerb.equals("HELO"))
			this.handleHELO(output);
		else if (smtpCommandVerb.equals("EHLO"))
			this.handleEHLO(output);
		else if (smtpCommandVerb.equals("MAIL"))
			this.handleMAIL(output);
		else if (smtpCommandVerb.equals("RCPT"))
			this.handleRCPT(output);
		else if (smtpCommandVerb.equals("DATA"))
			this.handleDATA(is, output);
		else if (smtpCommandVerb.equals("RSET"))
			this.handleRSET(output);
		else if (smtpCommandVerb.equals("QUIT"))
			this.handleQUIT(output);
		else if (smtpCommandVerb.equals("NOOP"))
			this.handleNOOP(output);
		else
			this.answerUnknownCommand(output);
	}

	private final void handleHELO(final PrintWriter output) {
		this.answerOK(output);
	}

	private final void handleEHLO(final PrintWriter output) {
		this.answerOK(output);
	}

	private final void handleQUIT(final PrintWriter output) {
		this.didNotQuit = false;
		output.println("221 MailRA Service closing transmission channel");
		output.flush();
	}

	private final void handleRCPT(final PrintWriter output) {
		this.answerOK(output);
	}

	private final void handleDATA(final InputStream is, final PrintWriter output) {
		try {
			output.println("354 Start mail input; end with <CRLF>.<CRLF>");
			output.flush();

			this.handleData(is);

			/*
			 * Open question: How can the DATA handler pass back work to MDBs?
			 * 
			 * Answer: We must split the general SMTP handling from the RA
			 * stuff, which means, we make handleDATA call an abstract
			 * handleMIME method, which is overwritten later.
			 * 
			 * But how can we do this in case of COMMAND pattern...?
			 */
			this.answerOK(output);
		} catch (final MessagingException e) {
			this.answerSyntaxErrorInParametersOrArguments(output);
		}
	}

	private final void handleMAIL(final PrintWriter output) {
		this.answerOK(output);
	}

	private final void handleRSET(final PrintWriter output) {
		this.answerOK(output);
	}

	private final void handleNOOP(final PrintWriter output) {
		this.answerOK(output);
	}

	private final void answerOK(final PrintWriter output) {
		output.println("250 OK");
		output.flush();
	}

	private final void answerUnknownCommand(final PrintWriter output) {
		output.println("500 Unknown command");
		output.flush();
	}

	private final void answerSyntaxErrorInParametersOrArguments(
			final PrintWriter output) {
		output.println("501 Syntax error in parameters or arguments");
		output.flush();
	}

	@Override
	public final void release() {
		/*
		 * Handling a call is not an endless work. It is acceptable that the
		 * application server has to create another thread instead of reusing
		 * ours. So we reject the application server's appeal to shutdown our
		 * work by just doing nothing here.
		 */
	}

}