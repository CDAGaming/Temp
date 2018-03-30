package se.rupy.http;

import java.security.*;
import java.util.*;

public class Chain extends LinkedList
{
    private int next;
    
    protected Link put(final Link link) {
        for (int i = 0; i < this.size(); ++i) {
            final Link tmp = super.get(i);
            if (link.index() == tmp.index()) {
                return this.set(i, link);
            }
            if (link.index() < tmp.index()) {
                this.add(i, link);
                return null;
            }
        }
        this.add(link);
        return null;
    }
    
    public void filter(final Event event) throws Event, Exception {
        for (int i = 0; i < this.size(); ++i) {
            final Service service = this.get(i);
            if (event.daemon().timeout > 0) {
                event.session(service);
            }
            if (event.daemon().host) {
                try {
                    final Object o = AccessController.doPrivileged((PrivilegedExceptionAction<Object>)new PrivilegedExceptionAction() {
                        @Override
                        public Object run() throws Exception {
                            try {
                                service.filter(event);
                                return null;
                            }
                            catch (Event event) {
                                return event;
                            }
                        }
                    }, event.daemon().archive(event.query().header("host")).access());
                    if (o != null) {
                        throw (Event)o;
                    }
                    continue;
                }
                catch (PrivilegedActionException e) {
                    if (e.getCause() != null) {
                        throw (Exception)e.getCause();
                    }
                    throw e;
                }
            }
            service.filter(event);
        }
    }
    
    protected void exit(final Session session, final int type) throws Exception {
        for (int i = 0; i < this.size(); ++i) {
            final Service service = this.get(i);
            if (session.daemon().host) {
                AccessController.doPrivileged((PrivilegedExceptionAction<Object>)new PrivilegedExceptionAction() {
                    @Override
                    public Object run() throws Exception {
                        service.session(session, type);
                        return null;
                    }
                }, session.daemon().control);
            }
            else {
                service.session(session, type);
            }
        }
    }
    
    protected void reset() {
        this.next = 0;
    }
    
    protected Link next() {
        if (this.next >= this.size()) {
            this.next = 0;
            return null;
        }
        return this.get(this.next++);
    }
    
    @Override
    public String toString() {
        final StringBuffer buffer = new StringBuffer();
        final Iterator it = this.iterator();
        buffer.append('[');
        while (it.hasNext()) {
            final Object object = it.next();
            String name = object.getClass().getName();
            if (name.equals("se.rupy.http.Event")) {
                buffer.append(object);
            }
            else {
                final int dollar = name.lastIndexOf(36);
                final int dot = name.lastIndexOf(46);
                if (dollar > 0) {
                    name = name.substring(dollar + 1);
                }
                else if (dot > 0) {
                    name = name.substring(dot + 1);
                }
                buffer.append(name);
            }
            if (it.hasNext()) {
                buffer.append(", ");
            }
        }
        buffer.append(']');
        return buffer.toString();
    }
    
    interface Link
    {
        int index();
    }
}
