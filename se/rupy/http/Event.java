package se.rupy.http;

import java.nio.channels.*;
import java.security.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.io.*;

public class Event extends Throwable implements Chain.Link
{
    public static final boolean LOG = true;
    static int READ;
    static int WRITE;
    static int VERBOSE;
    static int DEBUG;
    static Mime MIME;
    private static char[] BASE_24;
    private SocketChannel channel;
    private SelectionKey key;
    private Query query;
    private Reply reply;
    private Session session;
    private Daemon daemon;
    private Worker worker;
    private int index;
    private int interest;
    private String remote;
    private boolean close;
    private long touch;
    
    protected Event(final Daemon daemon, SelectionKey key, final int index) throws IOException {
        this.touch();
        (this.channel = ((ServerSocketChannel)key.channel()).accept()).configureBlocking(false);
        this.daemon = daemon;
        this.index = index;
        this.query = new Query(this);
        this.reply = new Reply(this);
        key = this.channel.register(key.selector(), Event.READ, this);
        key.selector().wakeup();
        this.key = key;
    }
    
    protected static String cookie(final String cookie, final String key) {
        String value = null;
        if (cookie != null) {
            final StringTokenizer tokenizer = new StringTokenizer(cookie, " ");
            while (tokenizer.hasMoreTokens()) {
                final String part = tokenizer.nextToken();
                final int equals = part.indexOf("=");
                if (equals > -1 && part.substring(0, equals).equals(key)) {
                    final String subpart = part.substring(equals + 1);
                    final int index = subpart.indexOf(";");
                    if (index > 0) {
                        value = subpart.substring(0, index);
                    }
                    else {
                        value = subpart;
                    }
                }
            }
        }
        return value;
    }
    
    public static String random(final int length) {
        final Random random = new Random();
        final StringBuffer buffer = new StringBuffer();
        while (buffer.length() < length) {
            buffer.append(Event.BASE_24[Math.abs(random.nextInt() % 24)]);
        }
        return buffer.toString();
    }
    
    protected int interest() {
        return this.interest;
    }
    
    protected void interest(final int interest) {
        this.interest = interest;
    }
    
    public Daemon daemon() {
        return this.daemon;
    }
    
    public Query query() {
        return this.query;
    }
    
    public Reply reply() {
        return this.reply;
    }
    
    public Session session() {
        return this.session;
    }
    
    public String remote() {
        return this.remote;
    }
    
    public boolean close() {
        return this.close;
    }
    
    public Worker worker() {
        return this.worker;
    }
    
    @Override
    public int index() {
        return this.index;
    }
    
    protected void close(final boolean close) {
        this.close = close;
    }
    
    protected void worker(final Worker worker) {
        this.worker = worker;
        try {
            this.register(Event.READ);
        }
        catch (CancelledKeyException e) {
            this.disconnect(e);
        }
    }
    
    protected SocketChannel channel() {
        return this.channel;
    }
    
    protected void log(final Object o) {
        this.log(o, Event.DEBUG);
    }
    
    protected void log(final Object o, final int level) {
        if (o instanceof Exception && this.daemon.debug) {
            this.daemon.out.print("[" + ((this.worker == null) ? "*" : ("" + this.worker.index())) + "-" + this.index + "] ");
            ((Exception)o).printStackTrace(this.daemon.out);
        }
        else if (this.daemon.debug || (this.daemon.verbose && level == Event.VERBOSE)) {
            this.daemon.out.println("[" + ((this.worker == null) ? "*" : ("" + this.worker.index())) + "-" + this.index + "] " + o);
        }
    }
    
    public long big(final String key) {
        return this.query.big(key);
    }
    
    public int medium(final String key) {
        return this.query.medium(key);
    }
    
    public short small(final String key) {
        return this.query.small(key);
    }
    
    public byte tiny(final String key) {
        return this.query.tiny(key);
    }
    
    public boolean bit(final String key) {
        return this.query.bit(key, true);
    }
    
    public String string(final String key) {
        return this.query.string(key);
    }
    
    public Input input() {
        return this.query.input();
    }
    
    public Output output() throws IOException {
        return this.reply.output();
    }
    
    protected void read() throws IOException {
        this.touch();
        if (!this.query.headers()) {
            this.disconnect(null);
        }
        this.remote = this.address();
        if (this.query.version() == null || !this.query.version().equalsIgnoreCase("HTTP/1.1")) {
            this.reply.code("505 Not Supported");
        }
        else if (!this.service(this.daemon.chain(this.query)) && !this.content() && !this.service(this.daemon.chain("null"))) {
            this.reply.code("404 Not Found");
            this.reply.output().print("<pre>'" + this.query.path() + "' was not found.</pre>");
        }
        this.finish();
    }
    
    protected String address() {
        String remote = this.query.header("x-forwarded-for");
        if (remote == null) {
            final InetSocketAddress address = (InetSocketAddress)this.channel.socket().getRemoteSocketAddress();
            remote = address.getAddress().getHostAddress();
        }
        this.log("remote " + remote, Event.VERBOSE);
        return remote;
    }
    
    protected boolean content() throws IOException {
        final Deploy.Stream stream = this.daemon.content(this.query);
        if (stream == null) {
            return false;
        }
        final String type = Event.MIME.content(this.query.path(), "application/octet-stream");
        this.reply.type(type);
        this.reply.modified(stream.date());
        if (this.query.modified() == 0L || this.query.modified() < this.reply.modified()) {
            Deploy.pipe(stream.input(), this.reply.output(stream.length()));
            this.log("content " + type, Event.VERBOSE);
        }
        else {
            this.reply.code("304 Not Modified");
        }
        return true;
    }
    
    protected boolean service(final Chain chain) throws IOException {
        if (chain == null) {
            return false;
        }
        try {
            chain.filter(this);
        }
        catch (Failure f) {
            throw f;
        }
        catch (Event event) {}
        catch (Exception e) {
            this.log(e);
            this.daemon.error(this, e);
            final StringWriter trace = new StringWriter();
            final PrintWriter print = new PrintWriter(trace);
            e.printStackTrace(print);
            this.reply.code("500 Internal Server Error");
            this.reply.output().print("<pre>" + trace.toString() + "</pre>");
        }
        return true;
    }
    
    protected void write() throws IOException {
        this.touch();
        this.service(this.daemon.chain(this.query));
        this.finish();
    }
    
    private void finish() throws IOException {
        final String log = this.daemon.access(this);
        this.reply.done();
        this.query.done();
        if (log != null) {
            this.daemon.access(log, this.reply.push());
        }
    }
    
    protected void register() throws IOException {
        if (this.interest != this.key.interestOps()) {
            this.log(((this.interest == Event.READ) ? "read" : "write") + " prereg " + this.interest + " " + this.key.interestOps() + " " + this.key.readyOps(), Event.DEBUG);
            this.key = this.channel.register(this.key.selector(), this.interest, this);
            this.log(((this.interest == Event.READ) ? "read" : "write") + " postreg " + this.interest + " " + this.key.interestOps() + " " + this.key.readyOps(), Event.DEBUG);
        }
        this.key.selector().wakeup();
        this.log(((this.interest == Event.READ) ? "read" : "write") + " wakeup", Event.DEBUG);
    }
    
    protected void register(final int interest) {
        this.interest(interest);
        try {
            if (this.channel.isOpen()) {
                this.register();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    protected int block(final Block block) throws Exception {
        final long max = System.currentTimeMillis() + this.daemon.delay;
        while (System.currentTimeMillis() < max) {
            this.register();
            final int available = block.fill(true);
            if (available > 0) {
                final long delay = this.daemon.delay - (max - System.currentTimeMillis());
                this.log("delay " + delay + " " + available, Event.VERBOSE);
                return available;
            }
            Thread.yield();
            this.worker.snooze(10L);
            this.key.selector().wakeup();
        }
        throw new Exception("IO timeout. (" + this.daemon.delay + ")");
    }
    
    protected void disconnect(final Exception e) {
        try {
            if (this.channel != null) {
                this.channel.close();
            }
            if (this.key != null) {
                this.key.cancel();
            }
            if (this.session != null) {
                this.session.remove(this);
            }
            if (this.daemon.debug) {
                this.log("disconnect " + e);
                if (e != null) {
                    e.printStackTrace();
                }
            }
            this.daemon.error(this, e);
        }
        catch (Exception de) {
            de.printStackTrace(this.daemon.out);
        }
        finally {
            this.daemon.events.remove(new Integer(this.index));
        }
    }
    
    protected final void session(final Service service) throws Exception {
        String key = cookie(this.query.header("cookie"), "key");
        if (key == null && this.query.method() == 1) {
            this.query.parse();
            final String cookie = this.query.string("cookie");
            key = ((cookie.length() > 0) ? cookie : null);
        }
        if (key != null) {
            this.session = this.daemon.session().get(key);
            if (this.session != null) {
                this.log("old key " + key, Event.VERBOSE);
                this.session.add(this);
                this.session.touch();
                return;
            }
        }
        int index = 0;
        if (this.daemon.host) {
            final Integer i = AccessController.doPrivileged((PrivilegedExceptionAction<Integer>)new PrivilegedExceptionAction() {
                @Override
                public Object run() throws Exception {
                    return new Integer(service.index());
                }
            }, this.daemon.control);
            index = i;
        }
        else {
            index = service.index();
        }
        if (index == 0 && !this.push()) {
            (this.session = new Session(this.daemon)).add(service);
            this.session.add(this);
            this.session.key(key);
            if (this.session.key() == null) {
                do {
                    key = random(this.daemon.cookie);
                } while (this.daemon.session().get(key) != null);
                this.session.key(key);
            }
            synchronized (this.daemon.session()) {
                this.log("new key " + this.session.key(), Event.VERBOSE);
                this.daemon.session().put(this.session.key(), this.session);
            }
        }
        try {
            service.session(this.session, 1);
        }
        catch (Exception e) {
            e.printStackTrace(this.daemon.out);
        }
    }
    
    @Override
    public String toString() {
        return String.valueOf(this.index);
    }
    
    public boolean push() {
        return this.reply.output.push();
    }
    
    public void touch() {
        this.touch = System.currentTimeMillis();
        if (this.worker != null) {
            this.worker.touch();
        }
    }
    
    protected long last() {
        return this.touch;
    }
    
    public void hold() throws IOException {
        this.reply.output.push = true;
    }
    
    static {
        Event.READ = 1;
        Event.WRITE = 4;
        Event.VERBOSE = 1;
        Event.DEBUG = 2;
        Event.MIME = new Mime();
        Event.READ = 1;
        Event.WRITE = 4;
        Event.BASE_24 = new char[] { 'B', 'C', 'D', 'F', 'G', 'H', 'J', 'K', 'M', 'P', 'Q', 'R', 'T', 'V', 'W', 'X', 'Y', '2', '3', '4', '6', '7', '8', '9' };
    }
    
    static class Mime extends Properties
    {
        public Mime() {
            try {
                this.load(Mime.class.getResourceAsStream("mime.txt"));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        String content(final String path, final String fail) {
            final int index = path.lastIndexOf(46) + 1;
            if (index > 0) {
                return this.getProperty(path.substring(index), fail);
            }
            return fail;
        }
    }
    
    interface Block
    {
        int fill(final boolean p0) throws IOException;
    }
}
