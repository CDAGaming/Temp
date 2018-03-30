package modinfo;

import net.minecraft.client.*;
import java.security.*;
import java.util.*;
import net.minecraft.client.resources.*;
import org.lwjgl.opengl.*;
import net.minecraft.server.integrated.*;
import modinfo.mp.v1.*;
import org.apache.logging.log4j.*;

public class ModInfo
{
    public static final String VERSION = "0.2";
    public static final Logger LOGGER;
    private final Minecraft minecraft;
    private final String trackingId;
    private final String modId;
    private final String modName;
    private final String modVersion;
    private Locale reportingLocale;
    private Config config;
    private Client client;
    
    public ModInfo(final String trackingId, final String reportingLanguageCode, final String modId, final String modName, final String modVersion, final boolean singleUse) {
        this.minecraft = Minecraft.func_71410_x();
        this.trackingId = trackingId;
        this.modId = modId;
        this.modName = modName;
        this.modVersion = modVersion;
        try {
            this.reportingLocale = this.getLocale(reportingLanguageCode);
            this.config = Config.getInstance(this.modId);
            this.client = this.createClient();
            if (singleUse) {
                this.singleUse();
            }
            else if (this.config.isEnabled()) {
                if (Config.generateStatusString(modId, false).equals(this.config.getStatus())) {
                    this.optIn();
                }
                else {
                    this.config.confirmStatus();
                }
            }
            else {
                this.optOut();
            }
        }
        catch (Throwable t) {
            ModInfo.LOGGER.log(Level.ERROR, "Unable to configure ModInfo", t);
        }
    }
    
    static UUID createUUID(final String... parts) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException nsae) {
            throw new InternalError("MD5 not supported");
        }
        for (final String part : parts) {
            md.update(part.getBytes());
        }
        final byte[] digest;
        final byte[] md5Bytes = digest = md.digest();
        final int n = 6;
        digest[n] &= 0xF;
        final byte[] array = md5Bytes;
        final int n2 = 6;
        array[n2] |= 0x30;
        final byte[] array2 = md5Bytes;
        final int n3 = 8;
        array2[n3] &= 0x3F;
        final byte[] array3 = md5Bytes;
        final int n4 = 8;
        array3[n4] |= (byte)128;
        long msb = 0L;
        long lsb = 0L;
        for (int i = 0; i < 8; ++i) {
            msb = (msb << 8 | (md5Bytes[i] & 0xFF));
        }
        for (int i = 8; i < 16; ++i) {
            lsb = (lsb << 8 | (md5Bytes[i] & 0xFF));
        }
        return new UUID(msb, lsb);
    }
    
    public final boolean isEnabled() {
        return this.client != null;
    }
    
    public void reportAppView() {
        try {
            if (this.isEnabled()) {
                final Payload payload = new Payload(Payload.Type.AppView);
                payload.add(this.appViewParams());
                payload.add(this.minecraftParams());
                this.client.send(payload);
            }
        }
        catch (Throwable t) {
            ModInfo.LOGGER.log(Level.ERROR, t.getMessage(), t);
        }
    }
    
    public void reportException(final Throwable e) {
        try {
            if (this.isEnabled()) {
                final String category = "Exception: " + e.toString();
                final String lineDelim = " / ";
                final int actionMaxBytes = Payload.Parameter.EventAction.getMaxBytes();
                final int labelMaxBytes = Payload.Parameter.EventLabel.getMaxBytes();
                final int maxBytes = actionMaxBytes + labelMaxBytes;
                final StackTraceElement[] stackTrace = e.getStackTrace();
                final ArrayList<Integer> byteLengths = new ArrayList<Integer>(stackTrace.length);
                int total = 0;
                for (int i = 0; i < stackTrace.length; ++i) {
                    final int byteLength = Payload.encode(stackTrace[i].toString() + " / ").getBytes().length;
                    if (total + byteLength > maxBytes) {
                        break;
                    }
                    total += byteLength;
                    byteLengths.add(i, byteLength);
                }
                int index = 0;
                final StringBuilder action = new StringBuilder(actionMaxBytes / 11);
                int actionTotal = 0;
                while (index < byteLengths.size()) {
                    final int byteLength2 = byteLengths.get(index);
                    if (actionTotal + byteLength2 > actionMaxBytes) {
                        break;
                    }
                    actionTotal += byteLength2;
                    action.append(stackTrace[index].toString() + " / ");
                    ++index;
                }
                final StringBuilder label = new StringBuilder(labelMaxBytes / 11);
                int labelTotal = 0;
                while (index < byteLengths.size()) {
                    final int byteLength3 = byteLengths.get(index);
                    if (labelTotal + byteLength3 > labelMaxBytes) {
                        break;
                    }
                    labelTotal += byteLength3;
                    label.append(stackTrace[index].toString() + " / ");
                    ++index;
                }
                this.reportEvent(category, action.toString(), label.toString());
            }
        }
        catch (Throwable t) {
            ModInfo.LOGGER.log(Level.ERROR, t.getMessage(), t);
        }
    }
    
    public void reportEvent(final String category, final String action, final String label) {
        try {
            if (this.isEnabled()) {
                final Payload payload = new Payload(Payload.Type.Event);
                payload.add(this.appViewParams());
                payload.put(Payload.Parameter.EventCategory, category);
                payload.put(Payload.Parameter.EventAction, action);
                payload.put(Payload.Parameter.EventLabel, label);
                this.client.send(payload);
            }
        }
        catch (Throwable t) {
            ModInfo.LOGGER.log(Level.ERROR, t.getMessage(), t);
        }
    }
    
    public void keepAlive() {
        try {
            if (this.isEnabled()) {
                final Payload payload = new Payload(Payload.Type.Event);
                payload.put(Payload.Parameter.EventCategory, "ModInfo");
                payload.put(Payload.Parameter.EventAction, "KeepAlive");
                payload.put(Payload.Parameter.NonInteractionHit, "1");
                this.client.send(payload);
            }
        }
        catch (Throwable t) {
            ModInfo.LOGGER.log(Level.ERROR, t.getMessage(), t);
        }
    }
    
    private Locale getLocale(final String languageCode) {
        final String english = "en_US";
        final List<String> langs = Arrays.asList(english);
        if (!english.equals(languageCode)) {
            langs.add(languageCode);
        }
        final Locale locale = new Locale();
        locale.func_135022_a(this.minecraft.func_110442_L(), (List)langs);
        return locale;
    }
    
    private String I18n(final String translationKey, final Object... parms) {
        return this.reportingLocale.func_135023_a(translationKey, parms);
    }
    
    private Client createClient() {
        final String salt = this.config.getSalt();
        final String username = this.minecraft.func_110432_I().func_111285_a();
        final UUID clientId = createUUID(salt, username, this.modId);
        return new Client(this.trackingId, clientId, this.config, Minecraft.func_71410_x().func_135016_M().func_135041_c().func_135034_a());
    }
    
    private Map<Payload.Parameter, String> minecraftParams() {
        final Map<Payload.Parameter, String> map = new HashMap<Payload.Parameter, String>();
        final Language language = this.minecraft.func_135016_M().func_135041_c();
        map.put(Payload.Parameter.UserLanguage, language.func_135034_a());
        final DisplayMode displayMode = Display.getDesktopDisplayMode();
        map.put(Payload.Parameter.ScreenResolution, displayMode.getWidth() + "x" + displayMode.getHeight());
        final StringBuilder desc = new StringBuilder("1.12.2");
        if (this.minecraft.field_71441_e != null) {
            final IntegratedServer server = this.minecraft.func_71401_C();
            final boolean multiplayer = server == null || server.func_71344_c();
            desc.append(", ").append(multiplayer ? this.I18n("menu.multiplayer", new Object[0]) : this.I18n("menu.singleplayer", new Object[0]));
        }
        map.put(Payload.Parameter.ContentDescription, desc.toString());
        return map;
    }
    
    private Map<Payload.Parameter, String> appViewParams() {
        final Map<Payload.Parameter, String> map = new HashMap<Payload.Parameter, String>();
        map.put(Payload.Parameter.ApplicationName, this.modName);
        map.put(Payload.Parameter.ApplicationVersion, this.modVersion);
        return map;
    }
    
    private void optIn() {
        final Payload payload = new Payload(Payload.Type.Event);
        payload.put(Payload.Parameter.EventCategory, "ModInfo");
        payload.put(Payload.Parameter.EventAction, "Opt In");
        this.createClient().send(payload, new Message.Callback() {
            @Override
            public void onResult(final Object result) {
                if (Boolean.TRUE.equals(result) && ModInfo.this.config.isEnabled()) {
                    ModInfo.this.config.confirmStatus();
                    ModInfo.LOGGER.info("ModInfo for " + ModInfo.this.config.getModId() + " has been re-enabled. Thank you!");
                }
            }
        });
    }
    
    public void singleUse() {
        if (Config.isConfirmedDisabled(this.config)) {
            return;
        }
        this.reportAppView();
        this.config.disable();
    }
    
    private void optOut() {
        if (Config.isConfirmedDisabled(this.config)) {
            ModInfo.LOGGER.info("ModInfo for " + this.modId + " is disabled");
        }
        else if (!this.config.isEnabled()) {
            final Payload payload = new Payload(Payload.Type.Event);
            payload.put(Payload.Parameter.EventCategory, "ModInfo");
            payload.put(Payload.Parameter.EventAction, "Opt Out");
            this.createClient().send(payload, new Message.Callback() {
                @Override
                public void onResult(final Object result) {
                    if (Boolean.TRUE.equals(result) && !ModInfo.this.config.isEnabled()) {
                        ModInfo.this.config.confirmStatus();
                        ModInfo.LOGGER.info("ModInfo for " + ModInfo.this.config.getModId() + " has been disabled");
                    }
                }
            });
        }
    }
    
    static {
        LOGGER = LogManager.getLogger("modinfo");
    }
}
