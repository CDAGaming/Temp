package se.rupy.http;

import java.nio.*;
import java.text.*;
import java.util.*;

public class Worker implements Runnable, Chain.Link
{
    private Daemon daemon;
    private ByteBuffer in;
    private ByteBuffer out;
    private byte[] chunk;
    private Thread thread;
    private Event event;
    private int index;
    private int lock;
    private boolean awake;
    private boolean alive;
    private long touch;
    private DateFormat date;
    
    protected Worker(final Daemon daemon, final int index) {
        this.daemon = daemon;
        this.index = index;
        this.in = ByteBuffer.allocateDirect(daemon.size);
        this.out = ByteBuffer.allocateDirect(daemon.size);
        (this.date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)).setTimeZone(TimeZone.getTimeZone("GMT"));
        this.alive = true;
        (this.thread = new Thread(this, "RupyWorker-" + index)).start();
    }
    
    protected DateFormat date() {
        return this.date;
    }
    
    protected ByteBuffer in() {
        this.touch();
        return this.in;
    }
    
    protected ByteBuffer out() {
        this.touch();
        return this.out;
    }
    
    protected byte[] chunk() {
        if (this.chunk == null) {
            this.chunk = new byte[this.daemon.size + Output.Chunked.OFFSET + 2];
        }
        return this.chunk;
    }
    
    protected void wakeup() {
        if (this.event != null && this.event.daemon().debug) {
            this.event.log("wakeup", Event.DEBUG);
        }
        this.touch();
        synchronized (this.thread) {
            this.thread.notify();
        }
        this.awake = true;
    }
    
    protected void touch() {
        this.touch = System.currentTimeMillis();
    }
    
    protected void snooze() {
        this.snooze(0L);
    }
    
    protected void snooze(final long delay) {
        if (this.event != null && this.event.daemon().debug) {
            this.event.log("snooze " + delay, Event.DEBUG);
        }
        synchronized (this.thread) {
            try {
                if (delay > 0L) {
                    if (this.awake) {
                        this.awake = false;
                        return;
                    }
                    this.thread.wait(delay);
                }
                else {
                    this.thread.wait();
                }
            }
            catch (InterruptedException e) {
                this.event.disconnect(e);
            }
            this.awake = false;
        }
    }
    
    protected Event event() {
        return this.event;
    }
    
    protected void event(final Event event) {
        this.event = event;
    }
    
    protected int lock() {
        return this.lock;
    }
    
    protected boolean busy() {
        if (this.event == null || this.touch <= 0L) {
            return false;
        }
        this.lock = (int)(System.currentTimeMillis() - this.touch);
        if (this.lock > this.daemon.delay) {
            this.reset(new Exception("Threadlock " + this.lock + " (" + this.event.query().path() + ")"));
            this.event = null;
            return false;
        }
        return this.event != null;
    }
    
    @Override
    public int index() {
        return this.index;
    }
    
    protected void stop() {
        synchronized (this.thread) {
            this.thread.notify();
        }
        this.alive = false;
    }
    
    @Override
    public String toString() {
        return String.valueOf(this.index);
    }
    
    @Override
    public void run() {
        this.touch = System.currentTimeMillis();
        while (this.alive) {
            try {
                if (this.event == null) {
                    continue;
                }
                if (this.event.push()) {
                    this.event.write();
                }
                else {
                    this.event.read();
                }
            }
            catch (Exception e) {
                this.reset(e);
            }
            finally {
                if (this.event != null) {
                    this.event.worker(null);
                    this.event = this.daemon.next(this);
                    if (this.event != null) {
                        this.event.worker(this);
                    }
                    else {
                        this.snooze();
                    }
                }
                else {
                    this.snooze();
                }
            }
        }
    }
    
    protected void reset(final Exception e) {
        if (this.event != null) {
            this.event.disconnect(e);
        }
        this.out.clear();
        this.in.clear();
    }
}
