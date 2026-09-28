package lol.bkd.antichud.update;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the "an update is waiting for you" notice on the title screen, which is the first thing a
 * player sees once the game has finished loading.
 *
 * <p>It is a plain static renderer rather than a screen so the player can keep using the main
 * menu while the notice sits in the corner.
 */
public final class UpdateBanner {
    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    private static final int GAP = 20;
    private static final int LINE_SPACING = 2;

    private static final int BACKGROUND = 0xB0101020;
    private static final int ACCENT = 0xFFFFC83D;
    private static final int MUTED = 0xFF9E9E9E;
    private static final int LINK = 0xFF4DD2FF;
    private static final int LINK_HOVERED = 0xFFFFD75E;

    /** Rectangle of the clickable line so the screen can route clicks to {@link #openDownloadPage()}. */
    public record LinkArea(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private record Line(String text, int colour, boolean link, int linkColour) {
    }

    private UpdateBanner() {
    }

    /**
     * Renders the notice in the bottom right corner of the screen.
     *
     * @return the clickable area when a link was drawn, otherwise null
     */
    public static LinkArea render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        List<Line> lines = lines();
        if (lines.isEmpty()) {
            return null;
        }

        int textWidth = 0;
        for (Line line : lines) {
            textWidth = Math.max(textWidth, font.width(line.text()));
        }

        int boxWidth = textWidth + PADDING * 2 + 2;
        int boxHeight = lines.size() * font.lineHeight + (lines.size() - 1) * LINE_SPACING + PADDING * 2;
        int boxX = Math.max(MARGIN, screenWidth - MARGIN - boxWidth);
        int boxY = Math.max(MARGIN, screenHeight - GAP - boxHeight - 10);

        int textX = boxX + PADDING + 2;
        int textY = boxY + PADDING;
        int linkX = 0;
        int linkY = 0;
        int linkWidth = 0;

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, BACKGROUND);
        graphics.fill(boxX, boxY, boxX + 2, boxY + boxHeight, ACCENT);

        for (Line line : lines) {
            boolean hovered = line.link() && contains(mouseX, mouseY, textX, textY, font.width(line.text()), font.lineHeight);
            graphics.drawString(font, line.text(), textX, textY, hovered ? line.linkColour() : line.colour());
            if (line.link()) {
                linkX = textX;
                linkY = textY;
                linkWidth = font.width(line.text());
            }
            textY += font.lineHeight + LINE_SPACING;
        }

        return linkWidth == 0 ? null : new LinkArea(linkX, linkY, linkWidth, font.lineHeight);
    }

    /** Opens the release page in the system browser. */
    public static void openDownloadPage() {
        UpdateChecker.UpdateInfo update = UpdateGate.info();
        String link = update == null ? UpdateChecker.RELEASES_PAGE : update.downloadLink();
        LinkOpener.open(link);
        System.out.println("[Antichud] Opened " + link);
    }

    private static List<Line> lines() {
        List<Line> lines = new ArrayList<>(3);

        switch (UpdateGate.status()) {
            case UPDATE_AVAILABLE -> {
                UpdateChecker.UpdateInfo update = UpdateGate.info();
                if (update == null) {
                    return List.of();
                }
                lines.add(new Line("antichud update available", ACCENT, false, 0));
                lines.add(new Line("installed " + update.currentVersion() + ", latest " + update.latestVersion(), MUTED, false, 0));
                lines.add(new Line("click to open the download page", LINK, true, LINK_HOVERED));
            }
            case CHECKING -> lines.add(new Line("checking for antichud updates...", MUTED, false, 0));
            default -> {
                return List.of();
            }
        }

        return lines;
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
