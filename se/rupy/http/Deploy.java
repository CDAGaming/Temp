package se.rupy.http;

import java.text.*;
import java.util.*;
import java.util.jar.*;
import java.security.*;
import java.io.*;
import java.net.*;

public class Deploy extends Service
{
    protected static String path;
    protected static String pass;
    
    public Deploy(final String path, final String pass) {
        Deploy.path = path;
        Deploy.pass = pass;
        new File(path).mkdirs();
    }
    
    public Deploy(final String path) {
        Deploy.path = path;
        new File(path).mkdirs();
    }
    
    @Override
    public String path() {
        return "/deploy";
    }
    
    @Override
    public void filter(final Event event) throws Event, Exception {
        final String name = event.query().header("file");
        final String size = event.query().header("size");
        final String pass = event.query().header("pass");
        if (name == null) {
            throw new Failure("File header missing.");
        }
        if (pass == null) {
            throw new Failure("Pass header missing.");
        }
        if (Deploy.pass == null) {
            if (size != null && size.length() > 0 && Integer.parseInt(size) > 2097152) {
                throw new Exception("Maximum deployable size is 2MB.");
            }
            final Properties properties = new Properties();
            properties.load(new FileInputStream(new File("passport")));
            final String port = properties.getProperty(name.substring(0, name.lastIndexOf(46)));
            if (port == null || !port.equals(pass)) {
                throw new Exception("Pass verification failed. (" + name + ")");
            }
        }
        else {
            if (!Deploy.pass.equals(pass)) {
                throw new Failure("Pass verification failed. (" + pass + ")");
            }
            if (Deploy.pass.equals("secret") && !event.remote().equals("127.0.0.1")) {
                throw new Failure("'secret' pass can only deploy from 127.0.0.1. (" + event.remote() + ")");
            }
        }
        final File file = new File(Deploy.path + name);
        final OutputStream out = new FileOutputStream(file);
        final InputStream in = event.query().input();
        Label_0389: {
            if (Deploy.pass == null) {
                try {
                    pipe(in, out, 1024, 2097152);
                    break Label_0389;
                }
                catch (IOException e) {
                    file.delete();
                    throw e;
                }
            }
            pipe(in, out, 1024);
        }
        out.flush();
        out.close();
        event.reply().output().print("Application '" + deploy(event.daemon(), file) + "' deployed.");
    }
    
    public static String deploy(final Daemon daemon, final File file) throws Exception {
        final Archive archive = new Archive(daemon, file);
        daemon.chain(archive);
        daemon.verify(archive);
        return archive.name();
    }
    
    public static String name(String name) {
        name = name.substring(0, name.indexOf("."));
        name = name.replace("/", ".");
        return name;
    }
    
    public static int pipe(final InputStream in, final OutputStream out) throws IOException {
        return pipe(in, out, 1024, 0);
    }
    
    public static int pipe(final InputStream in, final OutputStream out, final int length) throws IOException {
        return pipe(in, out, length, 0);
    }
    
    public static int pipe(final InputStream in, final OutputStream out, final int length, final int limit) throws IOException {
        final byte[] data = new byte[length];
        int total = 0;
        for (int read = in.read(data); read > -1; read = in.read(data)) {
            if (limit > 0 && total > limit) {
                throw new IOException("Max allowed bytes read. (" + limit + ")");
            }
            total += read;
            out.write(data, 0, read);
        }
        return total;
    }
    
    public static void main(final String[] args) {
        if (args.length > 2) {
            try {
                final URL url = new URL("http://" + args[0] + "/deploy");
                final File file = new File(args[1]);
                final InputStream in = new Client().send(url, file, args[2]);
                System.out.println(new SimpleDateFormat("H:mm").format(new Date()) + " " + Client.toString(in));
            }
            catch (ConnectException ce) {
                System.out.println("Connection failed, is there a server running on " + args[0] + "?");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            System.out.println("Usage: Deploy [host] [file] [pass]");
        }
    }
    
    public static class Archive extends ClassLoader
    {
        private AccessControlContext access;
        private HashSet service;
        private HashMap chain;
        private String name;
        private String host;
        private long date;
        Vector classes;
        static Archive deployer;
        
        Archive() {
            this.classes = new Vector();
            final PermissionCollection permissions = new Permissions();
            permissions.add(new SocketPermission("*", "listen,accept,resolve,connect"));
            permissions.add(new FilePermission("-", "read"));
            permissions.add(new FilePermission("-", "write"));
            permissions.add(new FilePermission("-", "delete"));
            permissions.add(new RuntimePermission("createClassLoader"));
            this.access = new AccessControlContext(new ProtectionDomain[] { new ProtectionDomain(null, permissions) });
        }
        
        Archive(final Daemon daemon, final File file) throws Exception {
            this.classes = new Vector();
            this.service = new HashSet();
            this.chain = new HashMap();
            this.name = file.getName();
            this.date = file.lastModified();
            final JarInputStream in = new JarInput(new FileInputStream(file));
            if (daemon.host) {
                this.host = this.name.substring(0, this.name.lastIndexOf(46));
                final String path = "app" + File.separator + this.host + File.separator;
                final PermissionCollection permissions = new Permissions();
                permissions.add(new SocketPermission("localhost", "resolve,connect"));
                permissions.add(new FilePermission(path + "-", "read"));
                permissions.add(new FilePermission(path + "-", "write"));
                permissions.add(new FilePermission(path + "-", "delete"));
                this.access = new AccessControlContext(new ProtectionDomain[] { new ProtectionDomain(null, permissions) });
                new File(path).mkdirs();
            }
            else {
                this.host = "content";
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            JarEntry entry = null;
            while ((entry = in.getNextJarEntry()) != null) {
                if (entry.getName().endsWith(".class")) {
                    Deploy.pipe(in, out);
                    final byte[] data = out.toByteArray();
                    out.reset();
                    final String name = name(entry.getName());
                    this.classes.add(new Small(name, data));
                }
                else {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    Big.write(this.host, "/" + entry.getName(), in);
                }
            }
            final int length = this.classes.size();
            final String missing = "";
            Small small = null;
            while (this.classes.size() > 0) {
                small = this.classes.elementAt(0);
                this.classes.removeElement(small);
                this.instantiate(small, daemon);
            }
        }
        
        @Override
        protected Class findClass(final String name) throws ClassNotFoundException {
            Small small = null;
            for (int i = 0; i < this.classes.size(); ++i) {
                small = this.classes.get(i);
                if (small.name.equals(name)) {
                    small.clazz = this.defineClass(small.name, small.data, 0, small.data.length);
                    this.resolveClass(small.clazz);
                    return small.clazz;
                }
            }
            throw new ClassNotFoundException();
        }
        
        private void instantiate(final Small small, final Daemon daemon) throws Exception {
            if (small.clazz == null) {
                small.clazz = this.defineClass(small.name, small.data, 0, small.data.length);
                this.resolveClass(small.clazz);
            }
            Class clazz = small.clazz.getSuperclass();
            boolean service = false;
            while (clazz != null) {
                if (clazz.getCanonicalName().equals("se.rupy.http.Service")) {
                    service = true;
                }
                clazz = clazz.getSuperclass();
            }
            if (service) {
                try {
                    if (daemon.host) {
                        final Service s = AccessController.doPrivileged((PrivilegedExceptionAction<Service>)new PrivilegedExceptionAction() {
                            @Override
                            public Object run() throws Exception {
                                return small.clazz.newInstance();
                            }
                        }, this.access());
                        this.service.add(s);
                    }
                    else {
                        this.service.add(small.clazz.newInstance());
                    }
                }
                catch (Exception e) {
                    if (daemon.verbose) {
                        daemon.out.println(small.name + " couldn't be instantiated!");
                    }
                }
            }
            if (daemon.debug) {
                daemon.out.println(small.name + (service ? "*" : ""));
            }
        }
        
        protected AccessControlContext access() {
            return this.access;
        }
        
        protected static String name(String name) {
            name = name.substring(0, name.indexOf("."));
            name = name.replace("/", ".");
            if (name.startsWith("WEB-INF.classes")) {
                name = name.substring(16);
            }
            return name;
        }
        
        public String name() {
            return this.name;
        }
        
        public String host() {
            return this.host;
        }
        
        public long date() {
            return this.date;
        }
        
        protected HashMap chain() {
            return this.chain;
        }
        
        protected HashSet service() {
            return this.service;
        }
        
        static {
            Archive.deployer = new Archive();
        }
    }
    
    static class Big implements Stream
    {
        private File file;
        private String name;
        private long date;
        
        public Big(final String host, final String name, final InputStream in, final long date) throws IOException {
            this.file = write(host, name, in);
            this.name = name;
            this.date = date - date % 1000L;
        }
        
        public Big(final File file) {
            final long date = file.lastModified();
            this.name = file.getName();
            this.file = file;
            this.date = date - date % 1000L;
        }
        
        static File write(final String host, final String name, final InputStream in) throws IOException {
            final String path = name.substring(0, name.lastIndexOf("/"));
            final String root = Deploy.path + host;
            new File(root + path).mkdirs();
            final File file = new File(root + name);
            file.createNewFile();
            final OutputStream out = new FileOutputStream(file);
            Deploy.pipe(in, out);
            out.flush();
            out.close();
            return file;
        }
        
        @Override
        public String name() {
            return this.name;
        }
        
        @Override
        public InputStream input() {
            try {
                return new FileInputStream(this.file);
            }
            catch (FileNotFoundException e) {
                return null;
            }
        }
        
        @Override
        public long length() {
            return this.file.length();
        }
        
        @Override
        public long date() {
            return this.date;
        }
    }
    
    static class Small implements Stream
    {
        private String name;
        private byte[] data;
        private long date;
        private Class clazz;
        
        public Small(final String name, final byte[] data) {
            this(name, data, 0L);
        }
        
        public Small(final String name, final byte[] data, final long date) {
            this.name = name;
            this.data = data;
            this.date = date - date % 1000L;
        }
        
        @Override
        public String name() {
            return this.name;
        }
        
        @Override
        public InputStream input() {
            return new ByteArrayInputStream(this.data);
        }
        
        @Override
        public long length() {
            return this.data.length;
        }
        
        @Override
        public long date() {
            return this.date;
        }
        
        byte[] data() {
            return this.data;
        }
        
        @Override
        public String toString() {
            return this.name;
        }
    }
    
    static class Client
    {
        InputStream send(final URL url, final File file, final String pass) throws IOException {
            return this.send(url, file, pass, true);
        }
        
        InputStream send(final URL url, final File file, final String pass, final boolean chunk) throws IOException {
            final HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("POST");
            OutputStream out = null;
            InputStream in = null;
            if (file != null) {
                conn.addRequestProperty("File", file.getName());
                conn.addRequestProperty("Size", "" + file.length());
                if (pass != null) {
                    conn.addRequestProperty("Pass", pass);
                }
                if (chunk) {
                    conn.setChunkedStreamingMode(0);
                }
                conn.setDoOutput(true);
                out = conn.getOutputStream();
                in = new FileInputStream(file);
                Deploy.pipe(in, out);
                out.flush();
                in.close();
            }
            final int code = conn.getResponseCode();
            if (code == 200) {
                in = conn.getInputStream();
            }
            else {
                if (code < 0) {
                    throw new IOException("HTTP response unreadable.");
                }
                in = conn.getErrorStream();
            }
            return in;
        }
        
        static String toString(final InputStream in) throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            Deploy.pipe(in, out);
            out.close();
            in.close();
            return new String(out.toByteArray());
        }
    }
    
    static class JarInput extends JarInputStream
    {
        public JarInput(final InputStream in) throws IOException {
            super(in);
        }
        
        @Override
        public void close() {
        }
    }
    
    interface Stream
    {
        String name();
        
        InputStream input();
        
        long length();
        
        long date();
    }
}
