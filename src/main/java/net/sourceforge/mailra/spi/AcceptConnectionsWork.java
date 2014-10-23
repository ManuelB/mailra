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

import static javax.resource.spi.work.WorkManager.INDEFINITE;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.logging.Logger;

import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkAdapter;
import javax.resource.spi.work.WorkEvent;
import javax.resource.spi.work.WorkException;
import javax.resource.spi.work.WorkManager;

public final class AcceptConnectionsWork implements Work {

	private static final Logger LOGGER = Logger
			.getLogger(AcceptConnectionsWork.class.getName());

	private final ServerSocket socket;

	private final WorkManager workManager;

	private final Collection<MessageEndpointFactory> messageEndpointFactories;

	private final int socketTimeout;

	AcceptConnectionsWork(final ServerSocket socket,
			final WorkManager workManager,
			final Collection<MessageEndpointFactory> messageEndpointFactories,
			final int socketTimout) {
		this.socket = socket;
		this.workManager = workManager;
		this.messageEndpointFactories = messageEndpointFactories;
		this.socketTimeout = socketTimout;
	}

	@Override
	public final void run() {
		try {
			while (true) {
				AcceptConnectionsWork.LOGGER.finer("Waiting for calls...");

				final Socket caller = this.socket.accept();

				AcceptConnectionsWork.LOGGER.finer("Accepted call.");

				try {
					this.workManager.scheduleWork(new HandleCallerWork(caller,
							this.messageEndpointFactories, this.socketTimeout),
							INDEFINITE, null, new WorkAdapter() {

								@Override
								public final void workRejected(final WorkEvent e) {
									try {
										AcceptConnectionsWork.LOGGER.severe(String
												.format("Application server rejected work, so incoming connection request gets dropped: %s",
														e));
										caller.close();
									} catch (final IOException x) {
										/*
										 * TCP seems to be screwed, so we just
										 * ignore this.
										 */
										AcceptConnectionsWork.LOGGER
												.warning(String.format(
														"IOException: ", e));
									}
								}
							});
				} catch (final WorkException e) {
					/*
					 * Since we cannot schedule the work, there is no chance to
					 * handle the incoming call. So we just close the connection
					 * in hope of a clever caller that will call again later.
					 */
					AcceptConnectionsWork.LOGGER
							.severe(String
									.format("Application server rejected work, so incoming connection request gets dropped: %s",
											e));
					caller.close();
				}
			}
		} catch (final IOException e) {
			/*
			 * It makes no sense to further wait for connections, since either
			 * we are shutting down currently (somebody called .close) or the
			 * port is broken. In any case, it is best to terminate our work.
			 */
			AcceptConnectionsWork.LOGGER.severe(String.format(
					"Shutting down due to IOException: %s", e));
		}
	}

	@Override
	public final void release() {
		/*
		 * Accepting new SMTP connections is essential for reliable mail
		 * transfer. Senders could be irritated by the fact that we do not
		 * respond to their connection attempt. So we reject the application
		 * server's appeal to shutdown our work by just doing nothing here.
		 */
	}

}