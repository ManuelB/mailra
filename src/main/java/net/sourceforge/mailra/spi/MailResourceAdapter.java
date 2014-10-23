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

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.logging.Logger;

import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.WorkException;

import net.sourceforge.rafc.spi.AbstractResourceAdapter;

/**
 * <code>ResourceAdapter</code> implementing the mail connector.
 * 
 * Other example:
 * https://github.com/nekop/twitter-resource-adapter/
 * 
 * Wildfly Resource Adapter:
 * https://docs.jboss.org/author/display/WFLY8/Resource+adapters
 * 
 * @author Markus KARG (markus-karg@users.sourceforge.net)
 */
// @Connector
public final class MailResourceAdapter extends AbstractResourceAdapter {

	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = Logger
			.getLogger(MailResourceAdapter.class.getName());

	private ServerSocket smtpSocket;

	@Override
	public void start(BootstrapContext bootstrapContext)
			throws ResourceAdapterInternalException {
		try {
			MailResourceAdapter.LOGGER.finer("Starting...");

			super.start(bootstrapContext);

			this.smtpSocket = new ServerSocket(this.portNumber, 50,
					this.ipAddress);
			this.getBootstrapContext()
					.getWorkManager()
					.startWork(
							new AcceptConnectionsWork(this.smtpSocket, this
									.getBootstrapContext().getWorkManager(),
									this.messageEndpointFactories,
									this.socketTimeout));

			MailResourceAdapter.LOGGER.info(String.format(
					"Started on address %s port %d.", this.ipAddress,
					this.portNumber));
		} catch (final IOException e) {
			throw new ResourceAdapterInternalException(
					"Cannot start due to IOException!", e);
		} catch (final WorkException e) {
			throw new ResourceAdapterInternalException(
					"Cannot start due to WorkException!", e);
		}
	}

	@Override
	public void stop() {
		MailResourceAdapter.LOGGER.finer("Stopping...");

		final ServerSocket socket = this.smtpSocket;
		this.smtpSocket = null;
		try {
			socket.close();
		} catch (final IOException e) {
			/*
			 * Since we're shutting down anyways, we gracefully ignore this
			 * exception.
			 */
		}

		MailResourceAdapter.LOGGER.info(String.format(
				"Stopped on address %s port %d.", this.ipAddress,
				this.portNumber));
	}

	/*
	 * Is accessed by two threads: (a) Mount/Unmount-Control-Thread (b) SMTP
	 * processor
	 * 
	 * TODO Can we have more than one message endpoint factory?
	 */
	private final Collection<MessageEndpointFactory> messageEndpointFactories = new LinkedList<MessageEndpointFactory>();

	@Override
	public final void endpointActivation(
			final MessageEndpointFactory messageEndpointFactory,
			final ActivationSpec activationSpec) throws ResourceException {
		MailResourceAdapter.LOGGER.finer("Activating endpoint...");

		this.messageEndpointFactories.add(messageEndpointFactory);

		MailResourceAdapter.LOGGER.fine("Endpoint activated.");
	}

	@Override
	public final void endpointDeactivation(
			final MessageEndpointFactory messageEndpointFactory,
			final ActivationSpec activationSpec) {
		MailResourceAdapter.LOGGER.finer("Dectivating endpoint...");

		/*
		 * Maybe that factory currently is in use by the SMTP processor...!
		 */
		synchronized (messageEndpointFactory) {
			this.messageEndpointFactories.remove(messageEndpointFactory);
		}

		MailResourceAdapter.LOGGER.fine("Endpoint deactivated.");
	}

	private InetAddress ipAddress;

	public final void setIPAddress(final String ipAddress)
			throws UnknownHostException {
		this.ipAddress = ipAddress == null ? null : InetAddress
				.getByName(ipAddress);
	}

	public final String getIPAddress() {
		return this.ipAddress == null ? null : this.ipAddress.toString();
	}

	private int portNumber = 25;

	public final void setPortNumber(final int portNumber) {
		this.portNumber = portNumber;
	}

	public final int getPortNumber() {
		return this.portNumber;
	}

	private int socketTimeout = 600000;

	public final int getSocketTimeout() {
		return this.socketTimeout;
	}

	public final void setSocketTimeout(final int socketTimeout) {
		this.socketTimeout = socketTimeout;
	}

}