package se.rupy.http;

import java.net.*;
import java.util.*;
import java.text.*;
import java.io.*;

public class Query extends Hash
{
    public static final int GET = 1;
    public static final int POST = 2;
    public static final int PUT = 4;
    public static final int DELETE = 8;
    public static final int HEAD = 16;
    static URLDecoder decoder;
    private String path;
    private String version;
    private String parameters;
    private HashMap headers;
    private Input input;
    private int length;
    private int method;
    private long modified;
    private boolean done;
    private boolean parsed;
    
    protected Query(final Event event) throws IOException {
        this.headers = new HashMap();
        this.input = new Input.Chunked(event);
    }
    
    protected boolean headers() throws IOException {
        this.headers.clear();
        String line;
        for (line = this.input.line(); line.equals(""); line = this.input.line()) {}
        final StringTokenizer http = new StringTokenizer(line, " ");
        final String method = http.nextToken();
        if (method.equalsIgnoreCase("get")) {
            this.method = 1;
        }
        else if (method.equalsIgnoreCase("post")) {
            this.method = 2;
            this.parsed = false;
        }
        else if (method.equalsIgnoreCase("save")) {
            this.method = 4;
            this.parsed = false;
        }
        else if (method.equalsIgnoreCase("delete")) {
            this.method = 8;
            this.parsed = false;
        }
        else {
            if (!method.equalsIgnoreCase("head")) {
                return false;
            }
            this.method = 16;
        }
        final String get = http.nextToken();
        final int index = get.indexOf(63);
        if (index > 0) {
            final URLDecoder decoder = Query.decoder;
            this.path = URLDecoder.decode(get.substring(0, index), "UTF-8");
            this.parameters = get.substring(index + 1);
            this.parsed = false;
        }
        else {
            final URLDecoder decoder2 = Query.decoder;
            this.path = URLDecoder.decode(get, "UTF-8");
            this.parameters = null;
        }
        this.version = http.nextToken();
        line = this.input.line();
        int lines = 0;
        while (line != null && !line.equals("")) {
            final int colon = line.indexOf(":");
            if (colon > -1) {
                final String name = line.substring(0, colon).toLowerCase();
                final String value = line.substring(colon + 1).trim();
                this.headers.put(name, value);
            }
            line = this.input.line();
            if (++lines > 30) {
                throw new IOException("Too many headers.");
            }
        }
        final String encoding = this.header("transfer-encoding");
        if (encoding != null && encoding.equalsIgnoreCase("chunked")) {
            this.length = -1;
        }
        else {
            final String content = this.header("content-length");
            if (content != null) {
                this.length = Integer.parseInt(content);
            }
            else {
                this.length = 0;
            }
        }
        final String since = this.header("if-modified-since");
        if (since != null && since.length() > 0) {
            try {
                this.modified = this.input.event().worker().date().parse(since).getTime();
            }
            catch (ParseException e) {
                this.modified = 0L;
            }
        }
        final String connection = this.header("connection");
        if (connection != null && connection.equalsIgnoreCase("close")) {
            this.input.event().close(true);
        }
        this.clear();
        this.input.event().log(method + " " + ((this.length > -1) ? ("" + this.length) : "*") + " " + this.path + ((this.parameters != null) ? ("?" + this.parameters) : ""), Event.VERBOSE);
        this.input.init();
        return true;
    }
    
    public void parse() throws Exception {
        this.parse(this.input.event().daemon().size);
    }
    
    public void parse(final int size) throws Exception {
        if (this.parsed) {
            return;
        }
        this.parsed = true;
        if (this.method == 2) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (Deploy.pipe(this.input, out, size, size) > 0) {
                this.parameters = new String(out.toByteArray());
            }
        }
        this.input.event().log("query " + this.parameters, Event.VERBOSE);
        if (this.parameters != null) {
            final StringTokenizer amp = new StringTokenizer(this.parameters, "&");
            while (amp.hasMoreTokens()) {
                final String equ = amp.nextToken();
                int pos = equ.indexOf(61);
                String key = null;
                String value = "false";
                if (pos == -1) {
                    pos = equ.length();
                    key = equ.substring(0, pos);
                }
                else {
                    key = equ.substring(0, pos);
                    String decode;
                    if (equ.length() > pos + 1) {
                        final URLDecoder decoder = Query.decoder;
                        decode = URLDecoder.decode(equ.substring(pos + 1), "UTF-8");
                    }
                    else {
                        decode = "";
                    }
                    value = decode;
                }
                this.put(key, value);
            }
        }
    }
    
    protected void done() throws IOException {
        this.input.end();
        this.modified = 0L;
    }
    
    public int method() {
        return this.method;
    }
    
    public String path() {
        return this.path;
    }
    
    public String version() {
        return this.version;
    }
    
    public String type() {
        return this.header("content-type");
    }
    
    public long modified() {
        return this.modified;
    }
    
    public int length() {
        return this.length;
    }
    
    public String header(final String name) {
        return this.headers.get(name.toLowerCase());
    }
    
    protected void header(final String name, final String value) {
        this.headers.put(name, value);
    }
    
    public String parameters() {
        return this.parameters;
    }
    
    public HashMap header() {
        return this.headers;
    }
    
    public Input input() {
        return this.input;
    }
    
    static {
        Query.decoder = new URLDecoder();
    }
}
