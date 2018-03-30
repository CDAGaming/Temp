package se.rupy.http;

public abstract class Service implements Chain.Link
{
    public static final int CREATE = 1;
    public static final int TIMEOUT = 2;
    public static final int DISCONNECT = 3;
    
    @Override
    public int index() {
        return 0;
    }
    
    public abstract String path();
    
    public void create(final Daemon daemon) throws Exception {
    }
    
    public void destroy() throws Exception {
    }
    
    public void session(final Session session, final int type) throws Exception {
    }
    
    public abstract void filter(final Event p0) throws Event, Exception;
}
