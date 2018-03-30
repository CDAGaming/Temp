package journeymap.client.waypoint;

import com.google.gson.*;
import com.google.common.cache.*;
import journeymap.client.io.*;
import java.io.*;
import java.nio.charset.*;
import com.google.common.io.*;
import journeymap.common.*;
import journeymap.common.log.*;
import journeymap.client.model.*;
import com.google.common.base.*;
import javax.annotation.*;
import com.google.common.collect.*;
import java.util.*;

@ParametersAreNonnullByDefault
public enum WaypointStore
{
    INSTANCE;
    
    private final Gson gson;
    private final Cache<String, Waypoint> cache;
    private final Cache<Long, Waypoint> groupCache;
    private final Set<Integer> dimensions;
    private boolean loaded;
    
    private WaypointStore() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.cache = (Cache<String, Waypoint>)CacheBuilder.newBuilder().build();
        this.groupCache = (Cache<Long, Waypoint>)CacheBuilder.newBuilder().build();
        this.dimensions = new HashSet<Integer>();
        this.loaded = false;
    }
    
    private boolean writeToFile(final Waypoint waypoint) {
        if (waypoint.isPersistent()) {
            File waypointFile = null;
            try {
                waypointFile = new File(FileHandler.getWaypointDir(), waypoint.getFileName());
                Files.write((CharSequence)this.gson.toJson((Object)waypoint), waypointFile, Charset.forName("UTF-8"));
                return true;
            }
            catch (Exception e) {
                Journeymap.getLogger().error(String.format("Can't save waypoint file %s: %s", waypointFile, LogFormatter.toString(e)));
                return false;
            }
        }
        return false;
    }
    
    public Collection<Waypoint> getAll() {
        return (Collection<Waypoint>)this.cache.asMap().values();
    }
    
    public Collection<Waypoint> getAll(final WaypointGroup group) {
        return Maps.filterEntries((Map)this.cache.asMap(), (Predicate)new Predicate<Map.Entry<String, Waypoint>>() {
            public boolean apply(@Nullable final Map.Entry<String, Waypoint> input) {
                return input != null && Objects.equals(group, input.getValue().getGroup());
            }
        }).values();
    }
    
    public void add(final Waypoint waypoint) {
        if (this.cache.getIfPresent((Object)waypoint.getId()) == null) {
            this.cache.put((Object)waypoint.getId(), (Object)waypoint);
        }
    }
    
    public void save(final Waypoint waypoint) {
        this.cache.put((Object)waypoint.getId(), (Object)waypoint);
        final boolean saved = this.writeToFile(waypoint);
        if (saved) {
            waypoint.setDirty(false);
        }
    }
    
    public void bulkSave() {
        for (final Waypoint waypoint : this.cache.asMap().values()) {
            if (waypoint.isDirty()) {
                final boolean saved = this.writeToFile(waypoint);
                if (!saved) {
                    continue;
                }
                waypoint.setDirty(false);
            }
        }
    }
    
    public void remove(final Waypoint waypoint) {
        this.cache.invalidate((Object)waypoint.getId());
        if (waypoint.isPersistent()) {
            final File waypointFile = new File(FileHandler.getWaypointDir(), waypoint.getFileName());
            if (waypointFile.exists()) {
                this.remove(waypointFile);
            }
        }
    }
    
    private void remove(final File waypointFile) {
        try {
            waypointFile.delete();
        }
        catch (Exception e) {
            Journeymap.getLogger().warn(String.format("Can't delete waypoint file %s: %s", waypointFile, e.getMessage()));
            waypointFile.deleteOnExit();
        }
    }
    
    public void reset() {
        this.cache.invalidateAll();
        this.dimensions.clear();
        this.loaded = false;
        if (Journeymap.getClient().getWaypointProperties().managerEnabled.get()) {
            this.load();
        }
    }
    
    private void load() {
        synchronized (this.cache) {
            final ArrayList<Waypoint> waypoints = new ArrayList<Waypoint>();
            File waypointDir = null;
            try {
                this.cache.invalidateAll();
                waypointDir = FileHandler.getWaypointDir();
                waypoints.addAll(new JmReader().loadWaypoints(waypointDir));
                this.load(waypoints, false);
                Journeymap.getLogger().info(String.format("Loaded %s waypoints from %s", this.cache.size(), waypointDir));
            }
            catch (Exception e) {
                Journeymap.getLogger().error(String.format("Error loading waypoints from %s: %s", waypointDir, LogFormatter.toString(e)));
            }
        }
    }
    
    public void load(final Collection<Waypoint> waypoints, final boolean forceSave) {
        for (final Waypoint waypoint : waypoints) {
            if (waypoint.isPersistent() && (forceSave || waypoint.isDirty())) {
                this.save(waypoint);
            }
            else {
                this.cache.put((Object)waypoint.getId(), (Object)waypoint);
            }
            this.dimensions.addAll(waypoint.getDimensions());
        }
        this.loaded = true;
    }
    
    public boolean hasLoaded() {
        return this.loaded;
    }
    
    public List<Integer> getLoadedDimensions() {
        return new ArrayList<Integer>(this.dimensions);
    }
}
