package net.sourceforge.mailra.spi;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Arrays;

/**
 * <code>InputStream</code> that closes when a specified token occurs on
 * <code>sourceStream</code>.
 * 
 * This stream class is useful whenever a reading application shall stop reading
 * from a stream when a special token is found in the stream. The token itself
 * will not be forwarded to the reading application, but just makes any
 * <code>read</code> to return -1 (which indicates to the caller that the
 * stream is closed).
 * 
 * @author Markus KARG (markus-karg@users.sourceforge.net)
 */
public final class StopTokenInputStream extends InputStream {

	private final PushbackInputStream sourceStream;

	private final byte[] stopToken;

	private boolean detectedStopToken;

	public StopTokenInputStream(final InputStream sourceStream, final byte[] stopToken) {
		this.sourceStream = new PushbackInputStream(sourceStream, stopToken.length);
		this.stopToken = stopToken;
	}

	@Override
	public final int read() throws IOException {
		if (this.detectedStopToken)
			return -1;

		final byte[] b = new byte[stopToken.length];

		this.sourceStream.read(b);

		if (Arrays.equals(b, this.stopToken)) {
			this.detectedStopToken = true;
			return -1;
		}

		this.sourceStream.unread(b);

		return this.sourceStream.read();
	}

}