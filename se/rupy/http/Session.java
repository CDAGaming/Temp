package se.rupy.http;

public class Session extends Hash
{
    private Daemon daemon;
    private Chain service;
    private Chain event;
    private boolean set;
    private String key;
    private String domain;
    private long date;
    private long expires;
    
    protected Session(final Daemon daemon) {
        this.daemon = daemon;
        this.service = new Chain();
        this.event = new Chain();
        this.touch();
    }
    
    protected synchronized void add(final Service service) {
        if (!this.service.contains(service)) {
            this.service.add(service);
        }
    }
    
    protected void add(final Event event) {
        if (!this.event.contains(event)) {
            this.event.add(event);
        }
    }
    
    public Chain event() {
        return this.event;
    }
    
    protected Daemon daemon() {
        return this.daemon;
    }
    
    protected void remove() throws Exception {
        this.remove(null);
    }
    
    protected synchronized boolean remove(final Event event) throws Exception {
        if (event == null) {
            this.event.clear();
            this.service.exit(this, 2);
            return true;
        }
        final boolean found = this.event.remove(event);
        if (this.event.isEmpty() && found) {
            this.service.exit(this, 3);
            return true;
        }
        return false;
    }
    
    public boolean set() {
        return this.set;
    }
    
    public void set(final boolean set) {
        this.set = set;
    }
    
    protected long expires() {
        return this.expires;
    }
    
    protected void expires(final long expires) {
        this.expires = expires;
    }
    
    public String key() {
        return this.key;
    }
    
    protected void key(final String key) {
        this.key = key;
        this.set = false;
    }
    
    public String domain() {
        return this.domain;
    }
    
    public void key(final String key, final String domain, final long expires) {
        if (key == null) {
            return;
        }
        this.daemon.session().remove(this.key);
        this.key = key;
        this.daemon.session().put(key, this);
        this.domain = domain;
        this.expires = expires;
        this.set = false;
    }
    
    public long date() {
        return this.date;
    }
    
    protected void touch() {
        this.date = System.currentTimeMillis();
    }
    
    @Override
    public String toString() {
        return this.key;
    }
}
