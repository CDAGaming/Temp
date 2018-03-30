package se.rupy.http;

import java.util.concurrent.*;
import java.security.*;
import java.net.*;
import java.text.*;
import java.io.*;
import java.nio.channels.*;
import java.util.*;

public class Daemon implements Runnable
{
    protected Properties properties;
    protected boolean verbose;
    protected boolean debug;
    protected boolean host;
    protected boolean alive;
    protected boolean panel;
    protected int threads;
    protected int timeout;
    protected int cookie;
    protected int delay;
    protected int size;
    protected int port;
    private int selected;
    private int valid;
    private int accept;
    private int readwrite;
    private HashMap archive;
    private HashMap service;
    protected ConcurrentHashMap events;
    protected ConcurrentHashMap session;
    private Chain workers;
    private Chain queue;
    private Heart heart;
    private Selector selector;
    private String pass;
    protected PrintStream out;
    protected PrintStream access;
    protected PrintStream error;
    private static DateFormat DATE;
    public AccessControlContext control;
    private Listener listener;
    
    public Daemon() {
        this(new Properties());
    }
    
    public Daemon(final Properties properties) {
        this.properties = properties;
        this.threads = Integer.parseInt(properties.getProperty("threads", "5"));
        this.cookie = Integer.parseInt(properties.getProperty("cookie", "4"));
        this.port = Integer.parseInt(properties.getProperty("port", "8000"));
        this.timeout = Integer.parseInt(properties.getProperty("timeout", "300")) * 1000;
        this.delay = Integer.parseInt(properties.getProperty("delay", "5000"));
        this.size = Integer.parseInt(properties.getProperty("size", "1024"));
        this.verbose = properties.getProperty("verbose", "false").toLowerCase().equals("true");
        this.debug = properties.getProperty("debug", "false").toLowerCase().equals("true");
        this.host = properties.getProperty("host", "false").toLowerCase().equals("true");
        this.panel = properties.getProperty("panel", "false").toLowerCase().equals("true");
        if (this.host) {
            final PermissionCollection permissions = new Permissions();
            this.control = new AccessControlContext(new ProtectionDomain[] { new ProtectionDomain(null, permissions) });
        }
        if (!this.verbose) {
            this.debug = false;
        }
        this.archive = new HashMap();
        this.service = new HashMap();
        this.session = new ConcurrentHashMap();
        this.events = new ConcurrentHashMap();
        this.workers = new Chain();
        this.queue = new Chain();
        try {
            this.out = new PrintStream(System.out, true, "UTF-8");
            if (properties.getProperty("log") != null || properties.getProperty("test", "false").toLowerCase().equals("true")) {
                this.log();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public Properties properties() {
        return this.properties;
    }
    
    protected void log() throws IOException {
        this.access = System.out;
        this.error = System.err;
        Daemon.DATE = new SimpleDateFormat("yy-MM-dd HH:mm:ss.SSS");
    }
    
    protected void error(final Event event, final Throwable t) throws IOException {
        if (this.error != null && t != null && !(t instanceof Failure.Close)) {
            final Calendar date = Calendar.getInstance();
            final StringBuilder b = new StringBuilder();
            b.append(Daemon.DATE.format(date.getTime()));
            b.append(' ');
            b.append(event.remote());
            b.append(' ');
            b.append(event.query().path());
            final String parameters = event.query().parameters();
            if (parameters != null) {
                b.append(' ');
                b.append(parameters);
            }
            b.append("\r\n");
            this.error.write(b.toString().getBytes("UTF-8"));
            t.printStackTrace(this.error);
        }
    }
    
    protected String access(final Event event) throws IOException {
        if (this.access != null && !event.reply().push()) {
            final Calendar date = Calendar.getInstance();
            final StringBuilder b = new StringBuilder();
            b.append(Daemon.DATE.format(date.getTime()));
            b.append(' ');
            b.append(event.remote());
            b.append(' ');
            b.append(event.query().path());
            b.append(' ');
            b.append(event.reply().code());
            final int length = event.reply().length();
            if (length > 0) {
                b.append(' ');
                b.append(length);
            }
            return b.toString();
        }
        return null;
    }
    
    protected void access(final String row, final boolean push) throws IOException {
        if (this.access != null) {
            final StringBuilder b = new StringBuilder();
            b.append(row);
            if (push) {
                b.append(' ');
                b.append('>');
            }
            b.append("\r\n");
            this.access.write(b.toString().getBytes("UTF-8"));
        }
    }
    
    public void init() {
        this.heart = new Heart();
        for (int threads = Integer.parseInt(this.properties.getProperty("threads", "5")), i = 0; i < threads; ++i) {
            this.workers.add(new Worker(this, i));
        }
        this.alive = true;
    }
    
    public boolean isAlive() {
        return this.alive;
    }
    
    public void start() {
        try {
            this.init();
            new Thread(this, "RupyDaemon").start();
        }
        catch (Exception e) {
            e.printStackTrace(this.out);
        }
    }
    
    public void stop() {
        for (final Worker worker : this.workers) {
            worker.stop();
        }
        this.workers.clear();
        this.alive = false;
        this.heart.stop();
        this.selector.wakeup();
    }
    
    public ConcurrentHashMap session() {
        return this.session;
    }
    
    protected Selector selector() {
        return this.selector;
    }
    
    protected void chain(final Deploy.Archive archive) throws Exception {
        final Deploy.Archive old = this.archive.get(archive.name());
        if (old != null) {
            for (final Service service : old.service()) {
                try {
                    if (this.host) {
                        AccessController.doPrivileged((PrivilegedExceptionAction<Object>)new PrivilegedExceptionAction() {
                            @Override
                            public Object run() throws Exception {
                                service.destroy();
                                return null;
                            }
                        }, archive.access());
                    }
                    else {
                        service.destroy();
                    }
                }
                catch (Exception e) {
                    e.printStackTrace(this.out);
                }
            }
        }
        for (final Service service : archive.service()) {
            this.add(archive.chain(), service, archive);
        }
        this.archive.put(archive.name(), archive);
    }
    
    public Deploy.Archive archive(String name) {
        if (!name.endsWith(".jar")) {
            name += ".jar";
        }
        if (!this.host) {
            return this.archive.get(name);
        }
        if (name.equals("host.rupy.se.jar")) {
            return Deploy.Archive.deployer;
        }
        final Deploy.Archive archive = this.archive.get(name);
        if (archive == null) {
            return this.archive.get("www." + name);
        }
        return archive;
    }
    
    public Object send(final Object message) throws Exception {
        if (this.listener == null) {
            return message;
        }
        return this.listener.receive(message);
    }
    
    public void set(final Listener listener) {
        this.listener = listener;
    }
    
    public void add(final Service service) throws Exception {
        this.add(this.service, service, null);
    }
    
    protected void add(final HashMap map, final Service service, final Deploy.Archive archive) throws Exception {
        String path = null;
        if (this.host) {
            path = AccessController.doPrivileged((PrivilegedExceptionAction<String>)new PrivilegedExceptionAction() {
                @Override
                public Object run() throws Exception {
                    return service.path();
                }
            }, this.control);
        }
        else {
            path = service.path();
        }
        if (path == null) {
            path = "null";
        }
        final StringTokenizer paths = new StringTokenizer(path, ":");
        while (paths.hasMoreTokens()) {
            path = paths.nextToken();
            Chain chain = map.get(path);
            if (chain == null) {
                chain = new Chain();
                map.put(path, chain);
            }
            final Service old = (Service)chain.put(service);
            if (this.host) {
                final String p = path;
                AccessController.doPrivileged((PrivilegedExceptionAction<Object>)new PrivilegedExceptionAction() {
                    @Override
                    public Object run() throws Exception {
                        if (old != null) {
                            throw new Exception(service.getClass().getName() + " with path '" + p + "' and index [" + service.index() + "] is conflicting with " + old.getClass().getName() + " for the same path and index.");
                        }
                        return null;
                    }
                }, this.control);
            }
            else if (old != null) {
                throw new Exception(service.getClass().getName() + " with path '" + path + "' and index [" + service.index() + "] is conflicting with " + old.getClass().getName() + " for the same path and index.");
            }
            if (this.verbose) {
                this.out.println(path + this.padding(path) + chain);
            }
            try {
                if (this.host) {
                    final Event event = AccessController.doPrivileged((PrivilegedExceptionAction<Event>)new PrivilegedExceptionAction() {
                        @Override
                        public Object run() throws Exception {
                            service.create(Daemon.this);
                            return null;
                        }
                    }, (archive == null) ? this.control : archive.access());
                }
                else {
                    service.create(this);
                }
            }
            catch (Exception e) {
                e.printStackTrace(this.out);
            }
        }
    }
    
    protected String padding(final String path) {
        final StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < 10 - path.length(); ++i) {
            buffer.append(' ');
        }
        return buffer.toString();
    }
    
    protected void verify(final Deploy.Archive archive) throws Exception {
        for (final String path : archive.chain().keySet()) {
            final Chain chain = archive.chain().get(path);
            for (int i = 0; i < chain.size(); ++i) {
                final Service service = chain.get(i);
                if (this.host) {
                    final HashMap a = this.archive;
                    final int j = i;
                    AccessController.doPrivileged((PrivilegedExceptionAction<Object>)new PrivilegedExceptionAction() {
                        @Override
                        public Object run() throws Exception {
                            if (j != service.index()) {
                                a.remove(archive.name());
                                throw new Exception(service.getClass().getName() + " with path '" + path + "' has index [" + service.index() + "] which is too high.");
                            }
                            return null;
                        }
                    }, this.control);
                }
                else if (i != service.index()) {
                    this.archive.remove(archive.name());
                    throw new Exception(service.getClass().getName() + " with path '" + path + "' has index [" + service.index() + "] which is too high.");
                }
            }
        }
    }
    
    protected Deploy.Stream content(final Query query) {
        if (this.host) {
            return this.content(query.header("host"), query.path());
        }
        return this.content(query.path());
    }
    
    protected Deploy.Stream content(final String path) {
        return this.content("content", path);
    }
    
    protected Deploy.Stream content(final String host, final String path) {
        File file = new File("app" + File.separator + host + File.separator + path);
        if (file.exists() && !file.isDirectory()) {
            return new Deploy.Big(file);
        }
        if (this.host) {
            file = new File("app" + File.separator + "www." + host + File.separator + path);
            if (file.exists() && !file.isDirectory()) {
                return new Deploy.Big(file);
            }
        }
        return null;
    }
    
    protected Chain chain(final Query query) {
        if (this.host) {
            return this.chain(query.header("host"), query.path());
        }
        return this.chain(query.path());
    }
    
    public Chain chain(final String path) {
        return this.chain("content", path);
    }
    
    public Chain chain(final String host, final String path) {
        synchronized (this.service) {
            final Chain chain = this.service.get(path);
            if (chain != null) {
                return chain;
            }
        }
        synchronized (this.archive) {
            if (this.host) {
                Deploy.Archive archive = this.archive.get(host + ".jar");
                if (archive == null) {
                    archive = this.archive.get("www." + host + ".jar");
                }
                if (archive != null) {
                    final Chain chain2 = archive.chain().get(path);
                    if (chain2 != null) {
                        return chain2;
                    }
                }
            }
            else {
                for (final Deploy.Archive archive2 : this.archive.values()) {
                    if (archive2.host().equals(host)) {
                        final Chain chain3 = archive2.chain().get(path);
                        if (chain3 != null) {
                            return chain3;
                        }
                        continue;
                    }
                }
            }
        }
        return null;
    }
    
    protected synchronized Event next(final Worker worker) {
        synchronized (this.queue) {
            if (this.queue.size() > 0) {
                if (this.debug) {
                    this.out.println("worker " + worker.index() + " found work " + this.queue);
                }
                return this.queue.remove(0);
            }
        }
        return null;
    }
    
    @Override
    public void run() {
        final String pass = this.properties.getProperty("pass", "");
        ServerSocketChannel server = null;
        try {
            this.selector = Selector.open();
            server = ServerSocketChannel.open();
            server.socket().bind(new InetSocketAddress(this.port));
            server.configureBlocking(false);
            server.register(this.selector, 16);
            final DecimalFormat decimal = (DecimalFormat)NumberFormat.getInstance();
            decimal.applyPattern("#.##");
            if (this.verbose) {
                this.out.println("daemon started\n- pass       \t" + pass + "\n- port       \t" + this.port + "\n- worker(s)  \t" + this.threads + " thread" + ((this.threads > 1) ? "s" : "") + "\n- session    \t" + this.cookie + " characters\n- timeout    \t" + decimal.format(this.timeout / 60000.0) + " minute" + ((this.timeout / 60000 > 1) ? "s" : "") + "\n- IO timeout \t" + this.delay + " ms.\n- IO buffer  \t" + this.size + " bytes\n- debug      \t" + this.debug + "\n- live       \t" + this.properties.getProperty("live", "false").toLowerCase().equals("true"));
            }
            if ((pass != null && pass.length() > 0) || this.host) {
                if (this.host) {
                    this.add(new Deploy("app" + File.separator));
                }
                else {
                    this.add(new Deploy("app" + File.separator, pass));
                }
                final File[] app = new File(Deploy.path).listFiles(new Filter());
                if (app != null) {
                    for (int i = 0; i < app.length; ++i) {
                        Deploy.deploy(this, app[i]);
                    }
                }
            }
            if (this.panel) {
                this.add(new Service() {
                    @Override
                    public String path() {
                        return "/panel";
                    }
                    
                    @Override
                    public void filter(final Event event) throws Event, Exception {
                        Iterator it = Daemon.this.workers.iterator();
                        event.output().println("<pre>workers: {size: " + Daemon.this.workers.size() + ", ");
                        while (it.hasNext()) {
                            final Worker worker = it.next();
                            event.output().print(" worker: {index: " + worker.index() + ", busy: " + worker.busy() + ", lock: " + worker.lock());
                            if (worker.event() != null) {
                                event.output().println(", ");
                                event.output().println("  event: {index: " + worker.event() + ", init: " + worker.event().reply().output.init + ", done: " + worker.event().reply().output.done + "}");
                                event.output().println(" }");
                            }
                            else {
                                event.output().println("}");
                            }
                        }
                        event.output().println("}");
                        event.output().println("events: {size: " + Daemon.this.events.size() + ", selected: " + Daemon.this.selected + ", valid: " + Daemon.this.valid + ", accept: " + Daemon.this.accept + ", readwrite: " + Daemon.this.readwrite + ", ");
                        it = Daemon.this.events.values().iterator();
                        while (it.hasNext()) {
                            final Event e = it.next();
                            event.output().println(" event: {index: " + e + ", last: " + (System.currentTimeMillis() - e.last()) + "}");
                        }
                        event.output().println("}</pre>");
                    }
                });
            }
            if (this.properties.getProperty("test", "false").toLowerCase().equals("true")) {
                new Test(this, 1);
            }
        }
        catch (Exception e) {
            e.printStackTrace(this.out);
            throw new RuntimeException(e);
        }
        int index = 0;
        Event event = null;
        SelectionKey key = null;
        while (this.alive) {
            try {
                this.selector.select();
                final Set set = this.selector.selectedKeys();
                int valid = 0;
                int accept = 0;
                int readwrite = 0;
                final int selected = set.size();
                final Iterator it = set.iterator();
                while (it.hasNext()) {
                    key = it.next();
                    it.remove();
                    if (key.isValid()) {
                        ++valid;
                        if (key.isAcceptable()) {
                            ++accept;
                            event = new Event(this, key, index++);
                            this.events.put(new Integer(event.index()), event);
                            event.log("accept ---");
                        }
                        else {
                            if (!key.isReadable() && !key.isWritable()) {
                                continue;
                            }
                            ++readwrite;
                            key.interestOps(0);
                            event = (Event)key.attachment();
                            final Worker worker = event.worker();
                            if (this.debug) {
                                if (key.isReadable()) {
                                    event.log("read ---");
                                }
                                if (key.isWritable()) {
                                    event.log("write ---");
                                }
                            }
                            if (key.isReadable() && event.push()) {
                                event.disconnect(null);
                            }
                            else if (worker == null) {
                                this.employ(event);
                            }
                            else {
                                worker.wakeup();
                            }
                        }
                    }
                }
                this.valid = valid;
                this.accept = accept;
                this.readwrite = readwrite;
                this.selected = selected;
            }
            catch (Exception e2) {
                if (event == null) {
                    System.out.println(this.events + " " + key);
                }
                else {
                    event.disconnect(e2);
                }
            }
        }
        try {
            if (this.selector != null) {
                this.selector.close();
            }
            if (server != null) {
                server.close();
            }
        }
        catch (IOException e3) {
            e3.printStackTrace(this.out);
        }
    }
    
    protected void queue(final Event event) {
        synchronized (this.queue) {
            this.queue.add(event);
        }
        if (this.debug) {
            this.out.println("queue " + this.queue.size());
        }
    }
    
    protected synchronized void employ(final Event event) {
        if (this.queue.size() > 0) {
            this.queue(event);
            return;
        }
        this.workers.reset();
        Worker worker = (Worker)this.workers.next();
        if (worker == null) {
            this.queue(event);
            return;
        }
        while (worker.busy()) {
            worker = (Worker)this.workers.next();
            if (worker == null) {
                this.queue(event);
                return;
            }
        }
        if (this.debug) {
            this.out.println("worker " + worker.index() + " hired. (" + this.queue.size() + ")");
        }
        event.worker(worker);
        worker.event(event);
        worker.wakeup();
    }
    
    protected void log(final PrintStream out) {
        if (out != null) {
            this.out = out;
        }
    }
    
    protected void log(final Object o) {
        if (this.out != null) {
            this.out.println(o);
        }
    }
    
    public static void main(final String[] args) {
        final Properties properties = new Properties();
        for (int i = 0; i < args.length; ++i) {
            final String flag = args[i];
            String value = null;
            if (flag.startsWith("-") && ++i < args.length) {
                value = args[i];
                if (value.startsWith("-")) {
                    --i;
                    value = null;
                }
            }
            if (value == null) {
                ((Hashtable<String, String>)properties).put(flag.substring(1).toLowerCase(), "true");
            }
            else {
                ((Hashtable<String, String>)properties).put(flag.substring(1).toLowerCase(), value);
            }
        }
        if (properties.getProperty("help", "false").toLowerCase().equals("true")) {
            System.out.println("Usage: java -jar http.jar -verbose");
            return;
        }
        new Daemon(properties).start();
    }
    
    class Filter implements FilenameFilter
    {
        @Override
        public boolean accept(final File dir, final String name) {
            return name.endsWith(".jar");
        }
    }
    
    class Heart implements Runnable
    {
        boolean alive;
        
        Heart() {
            this.alive = true;
            new Thread(this, "RupyHeart").start();
        }
        
        protected void stop() {
            this.alive = false;
        }
        
        @Override
        public void run() {
            while (this.alive) {
                try {
                    Thread.sleep(1000L);
                    Iterator it = Daemon.this.session.values().iterator();
                    while (it.hasNext()) {
                        final Session se = it.next();
                        if (System.currentTimeMillis() - se.date() > Daemon.this.timeout) {
                            it.remove();
                            se.remove();
                            if (!Daemon.this.debug) {
                                continue;
                            }
                            Daemon.this.out.println("session timeout " + se.key());
                        }
                    }
                    it = Daemon.this.workers.iterator();
                    while (it.hasNext()) {
                        final Worker worker = it.next();
                        worker.busy();
                    }
                    it = Daemon.this.events.values().iterator();
                    while (it.hasNext()) {
                        final Event event = it.next();
                        if (System.currentTimeMillis() - event.last() > 300000L) {
                            event.disconnect(null);
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace(Daemon.this.out);
                }
            }
        }
    }
    
    public interface Listener
    {
        Object receive(final Object p0) throws Exception;
    }
}
