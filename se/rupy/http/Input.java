package se.rupy.http;

import java.io.*;
import java.nio.*;

public abstract class Input extends InputStream implements Event.Block
{
    private boolean chunk;
    private boolean init;
    private byte[] one;
    private int available;
    private int length;
    private Event event;
    
    protected Input(final Event event) throws IOException {
        this.one = new byte[1];
        this.event = event;
    }
    
    protected void init() {
        this.chunk = (this.event.query().length() <= -1);
        if (this.event.daemon().verbose) {
            this.event.log("header " + this.length, Event.VERBOSE);
        }
        this.length = 0;
        this.init = true;
    }
    
    protected void end() {
        if (this.event.daemon().verbose && this.length > 0) {
            this.event.log("query " + this.length, Event.VERBOSE);
        }
        this.available = 0;
        this.init = false;
    }
    
    protected Event event() {
        return this.event;
    }
    
    protected boolean chunk() {
        return this.chunk;
    }
    
    protected int real() throws IOException {
        if (this.real(this.one, 0, 1) > 0) {
            return this.one[0] & 0xFF;
        }
        return -1;
    }
    
    protected int real(final byte[] b) throws IOException {
        return this.real(b, 0, b.length);
    }
    
    protected int real(final byte[] b, final int off, final int len) throws IOException {
        try {
            this.available = this.fill(false);
            if (this.available == 0) {
                if (this.init && !this.chunk && this.length >= this.event.query().length()) {
                    return -1;
                }
                this.available = this.event.block(this);
            }
            final int read = (this.available > len) ? len : this.available;
            this.event.worker().in().get(b, off, read);
            this.available -= read;
            this.length += read;
            return read;
        }
        catch (Failure.Close c) {
            throw c;
        }
        catch (IOException e) {
            Failure.chain(e);
        }
        catch (Exception e2) {
            throw (IOException)new IOException().initCause(e2);
        }
        return 0;
    }
    
    @Override
    public int available() {
        return this.available;
    }
    
    @Override
    public boolean markSupported() {
        return false;
    }
    
    @Override
    public int fill(final boolean debug) throws IOException {
        if (this.available > 0) {
            return this.available;
        }
        final ByteBuffer buffer = this.event.worker().in();
        buffer.clear();
        try {
            this.available = this.event.channel().read(buffer);
        }
        catch (IOException e) {
            throw (Failure.Close)new Failure.Close().initCause(e);
        }
        if (this.available > 0) {
            buffer.flip();
        }
        else if (this.available < 0) {
            throw new Failure.Close("Available: " + this.available);
        }
        return this.available;
    }
    
    public String line() throws IOException {
        final StringBuffer buffer = new StringBuffer("");
        while (buffer.length() <= 2048) {
            final int a = this.real();
            if (a == 13) {
                final int b = this.real();
                if (b == 10) {
                    return buffer.toString();
                }
                if (b <= -1) {
                    continue;
                }
                buffer.append((char)a);
                buffer.append((char)b);
            }
            else {
                if (a <= -1) {
                    continue;
                }
                buffer.append((char)a);
            }
        }
        throw new IOException("Line too long.");
    }
    
    static class Chunked extends Input
    {
        private byte[] one;
        private int length;
        
        protected Chunked(final Event event) throws IOException {
            super(event);
            this.one = new byte[1];
        }
        
        @Override
        public int read() throws IOException {
            if (this.read(this.one, 0, 1) > 0) {
                return this.one[0] & 0xFF;
            }
            return -1;
        }
        
        @Override
        public int read(final byte[] b) throws IOException {
            return this.read(b, 0, b.length);
        }
        
        @Override
        public int read(final byte[] b, final int off, int len) throws IOException {
            if (!this.chunk()) {
                return this.real(b, off, len);
            }
            if (this.length == 0) {
                boolean done = false;
                for (int c = this.real(); c != 10; c = this.real()) {
                    int val = 0;
                    if (c == 59 || c == 13) {
                        done = true;
                    }
                    else if (!done) {
                        if (c >= 48 && c <= 57) {
                            val = c - 48;
                        }
                        else if (c >= 97 && c <= 102) {
                            val = c - 97 + 10;
                        }
                        else {
                            if (c < 65 || c > 70) {
                                throw new IOException("Chunked input.");
                            }
                            val = c - 65 + 10;
                        }
                        this.length = this.length * 16 + val;
                    }
                }
                if (this.length == 0) {
                    return -1;
                }
            }
            if (len > this.length) {
                len = this.length;
            }
            final int read = this.real(b, off, len);
            if (read == this.length) {
                this.real();
                this.real();
            }
            if (read > 0) {
                this.length -= read;
            }
            return read;
        }
    }
}
