package lol.bkd.antichud.client.startup;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The jingle that plays the first time the loading overlay comes up.
 *
 * <p>It is played through {@code javax.sound.sampled} rather than Minecraft's own sound engine,
 * and that is not a shortcut. The overlay is on screen <i>because</i> the resource reload is still
 * running, so {@code SoundManager} has neither its sound event registry nor - more to the point -
 * its audio files yet, and {@code SoundEngine.play} answers {@code NOT_STARTED} for anything. The
 * engine does come up partway through, but only at the end of the reload, which is the one moment
 * the overlay goes away and the wrong moment for a startup jingle.
 *
 * <p>Same reason {@link StartupBackground} reads from the mod jar rather than the resource
 * manager: there is no pack stack to read from yet.
 *
 * <p>Vanilla decodes Ogg Vorbis and nothing else - {@code SoundBufferLibrary} hard-codes
 * {@code JOrbisAudioStream}, so a .wav cannot be handed to it at all. The JDK reads .wav, and
 * {@code java.desktop} is already in play: the background image is decoded with
 * {@code javax.imageio.ImageIO} from that same module.
 */
public final class StartupSound {

    private static final String FILE = "/assets/antichud/textures/gui/startup/startup.wav";

    /**
     * One shot per session. A manual resource reload draws the same overlay, and a jingle on every
     * F3+T would be dreadful.
     */
    private static final AtomicBoolean PLAYED = new AtomicBoolean();

    /**
     * A line that is still playing must not be collected out from under the mixer, so it is held
     * here until the clip reports that it has stopped.
     */
    private static volatile Clip playing;
    private StartupSound() {
    }

    /** Starts the jingle, unless it has already played this session or the game is muted. */
    public static void play() {
        if (!PLAYED.compareAndSet(false, true)) {
            return;
        }

        float volume = volume();
        if (volume <= 0.0F) {
            return;
        }

        // Off the render thread: the first javax.sound call in a JVM boots the whole sound
        // subsystem, which is far too slow to spend on the first frame of the loading screen.
        Thread thread = new Thread(() -> open(volume), "Antichud Startup Sound");
        thread.setDaemon(true);
        thread.start();
    }

    /** The player's master volume, so muting the game mutes this as well. */
    private static float volume() {
        return Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.MASTER);
    }

    private static void open(float volume) {
        try (InputStream in = StartupSound.class.getResourceAsStream(FILE)) {
            if (in == null) {
                return;
            }

            // AudioSystem marks and resets the stream while it sniffs the format, and neither a jar
            // entry stream nor a FileInputStream will do that.
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(in))) {
                Clip clip = AudioSystem.getClip();
                clip.open(audio);
                setVolume(clip, volume);

                // Freed again on STOP, so a finished jingle does not leave a dead clip holding a
                // system mixer line and its buffer for the rest of the session.
                playing = clip;
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                        if (playing == clip) {
                            playing = null;
                        }
                    }
                });
                clip.start();
            }
        } catch (Exception failure) {
            // No audio device, a format the JDK will not take, whatever it is. A startup jingle is
            // not worth a stack trace, and certainly not worth a crash on the way into the game.
            System.out.println("[Antichud] Startup sound could not be played: " + failure);
        }
    }

    /**
     * Applies the volume as a gain in decibels, which is the only shape a line takes it in.
     *
     * <p>Not {@code Clip.setGain}, which is not in every {@code java.desktop} - the JDK this builds
     * against has the interface without it. {@code FloatControl} is the portable spelling and is
     * in every JDK. A line that will not take the control is left at full volume rather than
     * treated as a failure, because playing too loudly beats not playing.
     */
    private static void setVolume(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        gain.setValue((float) (20.0 * Math.log10(volume)));
    }
}
