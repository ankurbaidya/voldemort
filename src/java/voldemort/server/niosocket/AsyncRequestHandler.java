/*
 * Copyright 2009 LinkedIn, Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.server.niosocket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import voldemort.server.protocol.RequestHandler;

/**
 * AsyncRequestHandler manages a Selector, SocketChannel, and RequestHandler
 * implementation. At the point that the run method is invoked, the Selector
 * with which the (socket) Channel has been registered has notified us that the
 * socket has data to read. The implementation basically wraps the Channel in
 * InputStream and OutputStream objects required by the RequestHandler
 * interface.
 * 
 * @author Kirk True
 * 
 * @see voldemort.server.protocol.RequestHandler
 */

public class AsyncRequestHandler implements Runnable {

    // This buffer size comes from voldemort.server.socket.SocketServerSession.
    // It's hard-coded there too. Perhaps it shouldn't be - I don't know...
    private static final int BUFFER_SIZE = 64000;

    private final Selector selector;

    private final SocketChannel socketChannel;

    private final RequestHandler requestHandler;

    private final ByteBufferBackedInputStream inputStream;

    private final ByteBufferBackedOutputStream outputStream;

    private final Logger logger = Logger.getLogger(getClass());

    public AsyncRequestHandler(Selector selector,
                               SocketChannel socketChannel,
                               RequestHandler requestHandler) {
        this.selector = selector;
        this.socketChannel = socketChannel;
        this.requestHandler = requestHandler;

        inputStream = new ByteBufferBackedInputStream(ByteBuffer.allocateDirect(BUFFER_SIZE));
        outputStream = new ByteBufferBackedOutputStream(ByteBuffer.allocateDirect(BUFFER_SIZE));

        if(logger.isInfoEnabled())
            logger.info("Accepting remote connection from "
                        + socketChannel.socket().getRemoteSocketAddress());
    }

    public void run() {
        SelectionKey selectionKey = socketChannel.keyFor(selector);

        try {
            if(selectionKey.isReadable()) {
                if(socketChannel.read(inputStream.getBuffer()) == -1)
                    throw new EOFException();

                if(requestHandler.isCompleteRequest(inputStream.getBuffer().array())) {
                    // If we have the full request, flip the buffer for reading
                    // and execute the request
                    inputStream.getBuffer().flip();
                    requestHandler.handleRequest(new DataInputStream(inputStream),
                                                 new DataOutputStream(outputStream));

                    // We're done with the input buffer, so clear it out for the
                    // next request...
                    inputStream.getBuffer().clear();

                    // We've written to the buffer in the handleRequest
                    // invocation, so now flip the output buffer.
                    outputStream.getBuffer().flip();

                    // We've got our output buffer ready, so let's let the
                    // Selector know.
                    selectionKey.interestOps(SelectionKey.OP_WRITE);
                }
            } else if(selectionKey.isWritable()) {
                // Write what we can now...
                socketChannel.write(outputStream.getBuffer());

                if(!outputStream.getBuffer().hasRemaining()) {
                    // If we don't have anything else to read, clear the output
                    // buffer.
                    outputStream.getBuffer().clear();

                    // The request is (finally) done, so let's signal the
                    // Selector that we're ready to take the next request.
                    selectionKey.interestOps(SelectionKey.OP_READ);
                }
            } else if(!selectionKey.isValid()) {
                if(logger.isEnabledFor(Level.WARN))
                    logger.warn("Selection key not valid");

                close(selectionKey);
            } else {
                if(logger.isEnabledFor(Level.WARN))
                    logger.warn("Unknown state, not readable, writable, or valid...");
            }
        } catch(EOFException e) {
            close(selectionKey);
        } catch(Exception e) {
            if(logger.isEnabledFor(Level.ERROR))
                logger.error(e.getMessage(), e);
        }
    }

    int getPort() {
        return socketChannel.socket().getPort();
    }

    private void close(SelectionKey selectionKey) {
        if(logger.isInfoEnabled())
            logger.info("Closing remote connection from "
                        + socketChannel.socket().getRemoteSocketAddress());

        try {
            socketChannel.socket().close();
        } catch(IOException ex) {
            if(logger.isEnabledFor(Level.WARN))
                logger.warn(ex, ex);
        }

        try {
            socketChannel.close();
        } catch(IOException ex) {
            if(logger.isEnabledFor(Level.WARN))
                logger.warn(ex, ex);
        }

        selectionKey.attach(null);
        selectionKey.cancel();
    }

}
