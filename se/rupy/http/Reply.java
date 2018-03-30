package se.rupy.http;

import java.util.*;
import java.io.*;

public class Reply
{
    public static int OK;
    public static int COMPLETE;
    public static int PROCESSING;
    private String type;
    private HashMap headers;
    protected Output output;
    private Event event;
    private long modified;
    private String code;
    
    protected Reply(final Event event) throws IOException {
        this.type = "text/html; charset=UTF-8";
        this.event = event;
        this.output = new Output.Chunked(this);
        this.reset();
    }
    
    protected void done() throws IOException {
        this.event.log("done " + this.output.push(), Event.DEBUG);
        if (!this.output.push()) {
            this.output.end();
            if (this.headers != null) {
                this.headers.clear();
            }
            this.reset();
        }
    }
    
    protected void reset() {
        this.modified = 0L;
        this.type = "text/html; charset=UTF-8";
        this.code = "200 OK";
    }
    
    protected Event event() {
        return this.event;
    }
    
    protected HashMap headers() {
        return this.headers;
    }
    
    public String code() {
        return this.code;
    }
    
    protected int length() {
        return this.output.length();
    }
    
    protected boolean push() {
        return this.output.push();
    }
    
    public void code(final String code) throws IOException {
        this.event.log("code", Event.DEBUG);
        this.code = code;
        this.output.init(0L);
    }
    
    public String type() {
        return this.type;
    }
    
    public void type(final String type) {
        this.type = type;
    }
    
    public void header(final String name, final String value) {
        if (this.headers == null) {
            this.headers = new HashMap();
        }
        this.headers.put(name, value);
    }
    
    protected long modified() {
        return this.modified;
    }
    
    protected void modified(final long modified) {
        this.modified = modified;
    }
    
    public Output output() throws IOException {
        return this.output(0L);
    }
    
    public Output output(final long length) throws IOException {
        this.event.log("output " + length, Event.DEBUG);
        this.output.init(length);
        return this.output;
    }
    
    public int wakeup() {
        if (this.output.complete()) {
            return Reply.COMPLETE;
        }
        if (this.event.worker() != null) {
            return Reply.PROCESSING;
        }
        this.event.daemon().employ(this.event);
        return Reply.OK;
    }
    
    static {
        Reply.OK = 0;
        Reply.COMPLETE = 1;
        Reply.PROCESSING = 2;
    }
}
