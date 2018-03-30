package se.rupy.http;

import java.util.*;

class Hash extends HashMap
{
    public long big(final String key) {
        return this.big(key, 0L);
    }
    
    public long big(final String key, final long fail) {
        final Object value = super.get(key);
        if (value == null) {
            return fail;
        }
        if (value instanceof Long) {
            return (long)value;
        }
        if (value instanceof Integer) {
            return (int)value;
        }
        if (value instanceof Short) {
            return (short)value;
        }
        if (value instanceof Byte) {
            return (byte)value;
        }
        if (!(value instanceof String)) {
            throw new ClassCastException();
        }
        final String text = (String)value;
        if (text.length() == 0) {
            return fail;
        }
        return Long.parseLong(text);
    }
    
    public int medium(final String key) {
        return this.medium(key, 0);
    }
    
    public int medium(final String key, final int fail) {
        final Object value = super.get(key);
        if (value == null) {
            return fail;
        }
        if (value instanceof Integer) {
            return (int)value;
        }
        if (value instanceof Short) {
            return (short)value;
        }
        if (value instanceof Byte) {
            return (byte)value;
        }
        if (!(value instanceof String)) {
            throw new ClassCastException();
        }
        final String text = (String)value;
        if (text.length() == 0) {
            return fail;
        }
        return Integer.parseInt(text);
    }
    
    public short small(final String key) {
        return this.small(key, (short)0);
    }
    
    public short small(final String key, final short fail) {
        final Object value = super.get(key);
        if (value == null) {
            return fail;
        }
        if (value instanceof Short) {
            return (short)value;
        }
        if (value instanceof Byte) {
            return (byte)value;
        }
        if (!(value instanceof String)) {
            throw new ClassCastException();
        }
        final String text = (String)value;
        if (text.length() == 0) {
            return fail;
        }
        return Short.parseShort(text);
    }
    
    public byte tiny(final String key) {
        return this.tiny(key, (byte)0);
    }
    
    public byte tiny(final String key, final byte fail) {
        final Object value = super.get(key);
        if (value == null) {
            return fail;
        }
        if (value instanceof Byte) {
            return (byte)value;
        }
        if (!(value instanceof String)) {
            throw new ClassCastException();
        }
        final String text = (String)value;
        if (text.length() == 0) {
            return fail;
        }
        return Byte.parseByte(text);
    }
    
    public boolean bit(final String key, final boolean exist) {
        final Object value = super.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (boolean)value;
        }
        if (!(value instanceof String)) {
            throw new ClassCastException();
        }
        if (exist) {
            return true;
        }
        final String s = (String)value;
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("on") || s.equalsIgnoreCase("yes");
    }
    
    public String string(final String key) {
        final String value = super.get(key);
        if (value == null) {
            return "";
        }
        return value;
    }
    
    public String string(final String key, final String fail) {
        final String value = super.get(key);
        if (value == null) {
            return fail;
        }
        return value;
    }
    
    public void put(final String key, final long value) {
        super.put(key, new Long(value));
    }
    
    public void put(final String key, final int value) {
        super.put(key, new Integer(value));
    }
    
    public void put(final String key, final short value) {
        super.put(key, new Short(value));
    }
    
    public void put(final String key, final byte value) {
        super.put(key, new Byte(value));
    }
    
    public void put(final String key, final boolean value) {
        super.put(key, new Boolean(value));
    }
    
    public String contents() {
        return super.toString();
    }
}
