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

package net.sourceforge.mailra.spi.inbound;

import net.sourceforge.rafc.spi.inbound.AbstractActivationSpec;

/**
 * Implementation of <code>ActivationSpec</code> for
 * <code>MessageListener</code>.
 * 
 * @author Markus KARG (markus-karg@users.sourceforge.net)
 */
@SuppressWarnings("serial")
// @Activation(messageListeners = { net.sourceforge.mailra.api.inbound.MessageListener.class })
public final class MessageListenerActivationSpec extends AbstractActivationSpec {
	// Intentionally left blank.
}
