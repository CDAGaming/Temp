package se.rupy.http;

import java.io.*;
import java.util.*;
import java.nio.*;

public abstract class Output extends OutputStream implements Event.Block
{
    public static final String EOL = "\r\n";
    private static final byte[] server;
    private static final byte[] close;
    private static final byte[] alive;
    private static final byte[] chunked;
    private byte[] one;
    protected int length;
    protected int size;
    protected Reply reply;
    protected boolean init;
    protected boolean push;
    protected boolean fixed;
    protected boolean done;
    
    Output(final Reply reply) throws IOException {
        this.one = new byte[1];
        this.reply = reply;
        this.size = reply.event().daemon().size;
    }
    
    public boolean complete() {
        return !this.push && this.done;
    }
    
    protected int length() {
        return this.length;
    }
    
    protected boolean push() {
        return this.push;
    }
    
    public void println(final Object o) throws IOException {
        this.write((o.toString() + "\r\n").getBytes("UTF-8"));
    }
    
    public void println(final long l) throws IOException {
        this.write((String.valueOf(l) + "\r\n").getBytes("UTF-8"));
    }
    
    public void println(final boolean b) throws IOException {
        this.write((String.valueOf(b) + "\r\n").getBytes("UTF-8"));
    }
    
    public void print(final Object o) throws IOException {
        this.write(o.toString().getBytes("UTF-8"));
    }
    
    public void print(final long l) throws IOException {
        this.write(String.valueOf(l).getBytes("UTF-8"));
    }
    
    public void print(final boolean b) throws IOException {
        this.write(String.valueOf(b).getBytes("UTF-8"));
    }
    
    protected void init(final long length) throws IOException {
        if (this.init) {
            this.reply.event().log("already inited", Event.DEBUG);
            return;
        }
        this.reply.event().log("init " + this.reply.event().query().version() + " " + length, Event.DEBUG);
        this.done = false;
        this.reply.event().interest(Event.WRITE);
        this.init = true;
        if (length > 0L) {
            this.fixed = true;
            this.headers(length);
        }
        else if (this.zero()) {
            this.headers(0L);
        }
        else {
            this.headers(-1L);
        }
    }
    
    protected void end() throws IOException {
        if (this.reply.event().daemon().debug) {
            this.reply.event().log("end", Event.DEBUG);
        }
        this.done = true;
        this.flush();
        if (this.reply.event().daemon().verbose && this.length > 0) {
            this.reply.event().log("reply " + this.length, Event.VERBOSE);
        }
        this.reply.event().interest(Event.READ);
        this.fixed = false;
        this.init = false;
        this.length = 0;
    }
    
    protected void headers(final long length) throws IOException {
        if (this.reply.event().daemon().verbose) {
            this.reply.event().log("code " + this.reply.code(), Event.VERBOSE);
        }
        this.wrote((this.reply.event().query().version() + " " + this.reply.code() + "\r\n").getBytes());
        this.wrote(("Date: " + this.reply.event().worker().date().format(new Date()) + "\r\n").getBytes());
        this.wrote(Output.server);
        if (!this.zero()) {
            this.wrote(("Content-Type: " + this.reply.type() + "\r\n").getBytes());
        }
        if (length > -1L) {
            this.wrote(("Content-Length: " + length + "\r\n").getBytes());
        }
        else {
            this.wrote(Output.chunked);
        }
        if (this.reply.modified() > 0L) {
            this.wrote(("Last-Modified: " + this.reply.event().worker().date().format(new Date(this.reply.modified())) + "\r\n").getBytes());
        }
        if (this.fixed && this.reply.event().daemon().properties.getProperty("live") != null) {
            this.wrote("Cache-Control: max-age=3600, must-revalidate\r\n".getBytes());
            this.wrote(("Expires: " + this.reply.event().worker().date().format(new Date(System.currentTimeMillis() + 31536000000L)) + "\r\n").getBytes());
        }
        if (this.reply.event().session() != null && !this.reply.event().session().set()) {
            final Session session = this.reply.event().session();
            final String cookie = "Set-Cookie: key=" + session.key() + ";" + ((session.expires() > 0L) ? (" expires=" + this.reply.event().worker().date().format(new Date(session.expires())) + ";") : "") + ((session.domain() != null) ? (" domain=" + session.domain() + ";") : "") + " path=/;";
            this.wrote((cookie + "\r\n").getBytes());
            this.reply.event().session().set(true);
            if (this.reply.event().daemon().verbose) {
                this.reply.event().log("cookie " + cookie, Event.VERBOSE);
            }
        }
        if (this.reply.event().close()) {
            this.wrote(Output.close);
        }
        else {
            this.wrote(Output.alive);
        }
        final HashMap headers = this.reply.headers();
        if (headers != null) {
            for (final String name : headers.keySet()) {
                final String value = this.reply.headers().get(name);
                this.wrote((name + ": " + value + "\r\n").getBytes());
            }
        }
        this.wrote("\r\n".getBytes());
    }
    
    protected void wrote(final int b) throws IOException {
        this.one[0] = (byte)b;
        this.wrote(this.one);
    }
    
    protected void wrote(final byte[] b) throws IOException {
        this.wrote(b, 0, b.length);
    }
    
    protected void wrote(final byte[] b, int off, int len) throws IOException {
        try {
            final ByteBuffer out = this.reply.event().worker().out();
            for (int remaining = out.remaining(); len > remaining; len -= remaining, remaining = out.remaining()) {
                out.put(b, off, remaining);
                this.internal(false);
                off += remaining;
            }
            if (len > 0) {
                out.put(b, off, len);
            }
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
    }
    
    protected void internal(final boolean debug) throws Exception {
        final ByteBuffer out = this.reply.event().worker().out();
        if (out.remaining() < this.size) {
            out.flip();
            while (out.remaining() > 0) {
                final int sent = this.fill(debug);
                if (debug) {
                    this.reply.event().log("sent " + sent + " remaining " + out.remaining(), Event.DEBUG);
                }
                if (sent == 0) {
                    this.reply.event().block(this);
                    if (!debug) {
                        continue;
                    }
                    this.reply.event().log("still in buffer " + out.remaining(), Event.DEBUG);
                }
            }
        }
        out.clear();
    }
    
    @Override
    public void flush() throws IOException {
        if (this.reply.event().daemon().debug) {
            this.reply.event().log("flush " + this.length, Event.DEBUG);
        }
        try {
            this.internal(true);
        }
        catch (Exception e) {
            throw (Failure.Close)new Failure.Close("No flush!").initCause(e);
        }
    }
    
    @Override
    public int fill(final boolean debug) throws IOException {
        final ByteBuffer out = this.reply.event().worker().out();
        int remaining = 0;
        if (debug) {
            remaining = out.remaining();
        }
        int sent = 0;
        try {
            sent = this.reply.event().channel().write(out);
        }
        catch (IOException e) {
            throw (Failure.Close)new Failure.Close().initCause(e);
        }
        if (debug) {
            this.reply.event().log("filled " + sent + " out of " + remaining, Event.DEBUG);
        }
        return sent;
    }
    
    public abstract void finish() throws IOException;
    
    protected boolean zero() {
        return this.reply.code().startsWith("302") || this.reply.code().startsWith("304") || this.reply.code().startsWith("505");
    }
    
    static {
        server = "Server: Rupy/0.4.3\r\n".getBytes();
        close = "Connection: Close\r\n".getBytes();
        alive = "Connection: Keep-Alive\r\n".getBytes();
        chunked = "Transfer-Encoding: Chunked\r\n".getBytes();
    }
    
    static class Chunked extends Output
    {
        public static int OFFSET;
        private int cursor;
        private int count;
        
        Chunked(final Reply reply) throws IOException {
            super(reply);
            this.cursor = Chunked.OFFSET;
            this.count = 0;
        }
        
        @Override
        public void write(final int b) throws IOException {
            this.reply.event().worker().chunk()[this.cursor++] = (byte)b;
            ++this.count;
            if (this.count == this.size) {
                this.write();
            }
        }
        
        @Override
        public void write(final byte[] b) throws IOException {
            this.write(b, 0, b.length);
        }
        
        @Override
        public void write(final byte[] b, int off, int len) throws IOException {
            this.length += len;
            if (this.fixed) {
                this.wrote(b, off, len);
                return;
            }
            final byte[] chunk = this.reply.event().worker().chunk();
            final int remain = this.size - this.count;
            if (len > remain) {
                System.arraycopy(b, off, chunk, this.cursor, remain);
                this.count = this.size;
                this.write();
                len -= remain;
                off += remain;
                while (len > this.size) {
                    System.arraycopy(b, off, chunk, Chunked.OFFSET, this.size);
                    len -= this.size;
                    off += this.size;
                    this.count = this.size;
                    this.write();
                }
                this.cursor = Chunked.OFFSET;
            }
            if (len > 0) {
                System.arraycopy(b, off, chunk, this.cursor, len);
                this.count += len;
                this.cursor += len;
            }
        }
        
        protected void write() throws IOException {
            final byte[] chunk = this.reply.event().worker().chunk();
            final char[] header = Integer.toHexString(this.count).toCharArray();
            final int length = header.length;
            final int start = 4 - length;
            int cursor;
            for (cursor = 0; cursor < length; ++cursor) {
                chunk[start + cursor] = (byte)header[cursor];
            }
            chunk[start + cursor++] = 13;
            chunk[start + cursor++] = 10;
            chunk[start + cursor++ + this.count] = 13;
            chunk[start + cursor++ + this.count] = 10;
            this.wrote(chunk, start, cursor + this.count);
            this.count = 0;
            this.cursor = Chunked.OFFSET;
        }
        
        @Override
        public void finish() throws IOException {
            if (this.complete()) {
                throw new IOException("Reply already complete.");
            }
            this.push = false;
        }
        
        @Override
        public void flush() throws IOException {
            if (this.init) {
                if (this.zero()) {
                    if (this.reply.event().daemon().debug) {
                        this.reply.event().log("length " + this.length, Event.DEBUG);
                    }
                }
                else if (!this.fixed) {
                    if (this.reply.event().daemon().debug) {
                        this.reply.event().log("chunk flush " + this.count + " " + this.complete(), Event.DEBUG);
                    }
                    if (this.count > 0) {
                        this.write();
                    }
                    if (this.complete()) {
                        this.write();
                    }
                }
            }
            else if (!this.fixed) {
                if (this.reply.event().daemon().debug) {
                    this.reply.event().log("asynchronous push " + this.count, Event.DEBUG);
                }
                this.push = true;
            }
            super.flush();
        }
        
        static {
            Chunked.OFFSET = 6;
        }
    }
}
