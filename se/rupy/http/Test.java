package se.rupy.http;

import java.net.*;
import java.io.*;

class Test implements Runnable
{
    static final String intro = "Parallel testing with one worker thread:\r\n- Fixed and chunked, read and write.\r\n- Asynchronous non-blocking reply.\r\n- Session creation and timeout.\r\n- Exception handling.\r\nNOTICE: The test receives and sends the bin/http.jar\r\nwhich is ~60kb, if you wonder why it takes time.\r\n             ---o---";
    static final int other_count = 300;
    static final int comet_count = 5;
    static final int comet_sleep = 30;
    static final String[] unit;
    static final String original = "bin/http.jar";
    protected boolean failed;
    protected int loop;
    protected int done;
    protected int http;
    protected String host;
    protected String name;
    protected Service service;
    protected Daemon daemon;
    protected Thread thread;
    protected long time;
    protected static File file;
    protected static Test test;
    
    protected Test(final Daemon daemon, final int loop) {
        this.loop = loop;
        Test.file = new File("bin/http.jar");
        this.daemon = daemon;
        Test.test = this;
        (this.thread = new Thread(this, "RupyTest-" + loop)).start();
    }
    
    protected Test(final String host, final String name, final int loop) throws IOException {
        this.host = host;
        this.name = name;
        this.loop = loop;
        this.service = new Service(name);
    }
    
    protected Service service() {
        return this.service;
    }
    
    protected boolean failed() {
        return this.failed || this.service.failed();
    }
    
    protected String name() {
        return this.name;
    }
    
    protected void done(final Test test) {
        ++this.done;
        ++this.http;
        System.out.println(this.done + "/" + (Test.unit.length + 2) + " Done: " + test.name + " (" + test.loop + ")");
        if (this.http == Test.unit.length) {
            System.out.println(900 * this.loop + 5 * this.loop + " dynamic requests in " + (System.currentTimeMillis() - this.time) + " ms.");
        }
        this.done();
    }
    
    protected void done(final String text) {
        ++this.done;
        System.out.println(this.done + "/" + (Test.unit.length + 2) + " Done: " + text);
        this.done();
    }
    
    protected void done() {
        if (this.done == Test.unit.length + 2) {
            synchronized (this.thread) {
                this.thread.notify();
            }
        }
    }
    
    void save(final String name, final InputStream in) {
        int read = 0;
        try {
            final File file = new File(name);
            final OutputStream out = new FileOutputStream(file);
            read = Deploy.pipe(in, out);
            out.flush();
            out.close();
            if (file.length() != Test.file.length()) {
                this.failed = true;
            }
        }
        catch (Exception e) {
            System.out.println(name + " failed. (" + read + ")");
            e.printStackTrace();
            this.failed = true;
        }
    }
    
    @Override
    public void run() {
        try {
            if (this.daemon != null) {
                this.test(this.daemon);
                return;
            }
            for (int i = 0; i < this.loop; ++i) {
                this.connect();
            }
        }
        catch (ConnectException ce) {
            System.out.println("Connection failed, is there a server on " + this.host + "?");
        }
        catch (Throwable e) {
            e.printStackTrace();
            this.failed = true;
            if (this.daemon != null) {
                System.exit(1);
            }
        }
        finally {
            Test.test.done(this);
        }
    }
    
    void test(final Daemon daemon) throws Exception {
        System.out.println("Parallel testing with one worker thread:\r\n- Fixed and chunked, read and write.\r\n- Asynchronous non-blocking reply.\r\n- Session creation and timeout.\r\n- Exception handling.\r\nNOTICE: The test receives and sends the bin/http.jar\r\nwhich is ~60kb, if you wonder why it takes time.\r\n             ---o---");
        Thread.sleep(100L);
        this.time = System.currentTimeMillis();
        System.out.println("START");
        final Test[] test = new Test[Test.unit.length];
        for (int i = 0; i < test.length; ++i) {
            test[i] = new Test("localhost:" + daemon.port, Test.unit[i], this.loop * (Test.unit[i].equals("comet") ? 5 : 300));
            daemon.add(test[i].service());
            final Thread thread = new Thread(test[i], "RupyTestCase-" + i);
            thread.start();
        }
        synchronized (this.thread) {
            this.thread.wait();
        }
        boolean failed = false;
        for (int j = 0; j < test.length; ++j) {
            if (test[j].failed()) {
                failed = true;
            }
            new File(test[j].name).deleteOnExit();
        }
        System.out.println(failed ? "UNIT FAILED! (see log/error.txt)" : "UNIT SUCCESSFUL!");
        System.exit(0);
    }
    
    private void connect() throws IOException {
        final URL url = new URL("http://" + this.host + "/" + this.name);
        if (this.name.equals("error")) {
            final String error = Deploy.Client.toString(new Deploy.Client().send(url, null, null, true));
            if (error.indexOf("Error successful") == -1) {
                this.failed = true;
            }
        }
        else {
            this.save(this.name, new Deploy.Client().send(url, Test.file, null, true));
        }
    }
    
    static {
        unit = new String[] { "comet", "chunk", "fixed", "error" };
    }
    
    static class Service extends se.rupy.http.Service implements Runnable
    {
        protected static boolean session;
        protected static boolean timeout;
        protected String path;
        protected Event event;
        protected boolean failed;
        
        public Service(final String name) {
            this.path = "/" + name;
        }
        
        @Override
        public String path() {
            return this.path;
        }
        
        protected boolean failed() {
            return this.failed;
        }
        
        @Override
        public void session(final Session session, final int type) {
            if (type == 1) {
                if (!Service.session) {
                    Service.session = true;
                    Test.test.done("Session successful.");
                }
            }
            else if (type == 2) {
                if (!Service.timeout) {
                    Service.timeout = true;
                    Test.test.done("Timeout successful.");
                }
            }
            else {
                System.out.println("Socket closed. (" + this.path + ")");
            }
        }
        
        @Override
        public void filter(final Event event) throws Event, Exception {
            try {
                this.work(event);
            }
            catch (Exception e) {
                e.printStackTrace();
                this.failed = true;
                throw e;
            }
            if (this.path.equals("/error")) {
                throw new Exception("Error successful.");
            }
        }
        
        private void work(final Event event) throws Exception {
            if (this.path.equals("/chunk")) {
                this.load(event);
                this.write(event.output());
            }
            else if (this.path.equals("/fixed")) {
                this.load(event);
                this.write(event.reply().output(Test.file.length()));
            }
            else if (this.path.equals("/comet")) {
                if (event.push()) {
                    this.write(event.output());
                    event.output().finish();
                }
                else {
                    this.load(event);
                    this.event = event;
                    new Thread(this, "RupyTestWork").start();
                }
            }
        }
        
        private void load(final Event event) throws IOException {
            final int read = this.read(event.input());
            if (read != Test.file.length()) {
                this.failed = true;
            }
        }
        
        @Override
        public void run() {
            try {
                Thread.sleep(30L);
                this.event.reply().wakeup();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        int read(final InputStream in) throws IOException {
            final OutputStream out = new ByteArrayOutputStream();
            return Deploy.pipe(in, out);
        }
        
        int write(final OutputStream out) throws IOException {
            final File file = new File("bin/http.jar");
            final InputStream in = new FileInputStream(file);
            return Deploy.pipe(in, out);
        }
    }
}
