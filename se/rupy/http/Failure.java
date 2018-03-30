package se.rupy.http;

import java.io.*;

class Failure extends IOException
{
    protected Failure(final String message) {
        super(message);
    }
    
    protected Failure(final Helper helper) {
        super(helper.getRoot().getMessage());
    }
    
    protected static void chain(final Throwable t) throws Failure {
        throw (Failure)new Failure(new Helper(t)).initCause(t);
    }
    
    protected static void chain(final String message, final Throwable t) throws Failure {
        throw (Failure)new Failure(message).initCause(t);
    }
    
    static class Helper
    {
        Throwable root;
        
        protected Helper(Throwable t) {
            while (t.getCause() != null) {
                t = t.getCause();
            }
            this.root = t;
        }
        
        protected Throwable getRoot() {
            return this.root;
        }
    }
    
    static class Close extends IOException
    {
        public Close() {
        }
        
        public Close(final String message) {
            super(message);
        }
    }
}
