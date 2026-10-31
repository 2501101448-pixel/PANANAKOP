package pananakop;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;

public class Player {
    private static final int IDLE_FRAME_COUNT = 4;
    private static final int RUN_FRAME_COUNT = 8;
    private static final int JUMP_START_FRAME_COUNT = 4;
    private static final int JUMP_END_FRAME_COUNT = 4;
    private static final int ATTACK_FRAME_COUNT = 7;
    private static final int BLOCK_FRAME_COUNT = 5;
    private static final long IDLE_FRAME_TIME = 120_000_000L;
    private static final long RUN_FRAME_TIME = 80_000_000L;
    private static final long SPRINT_FRAME_TIME = 55_000_000L;
    private static final long JUMP_FRAME_TIME = 90_000_000L;
    private static final long ATTACK_FRAME_TIME = 75_000_000L;
    private static final long ATTACK_TRANSITION_TIME = 60_000_000L;
    private static final long BLOCK_FRAME_TIME = 90_000_000L;
    private static final long BLOCK_TRANSITION_TIME = 80_000_000L;
    private static final int SPRITE_DRAW_WIDTH = 220;
    private static final int SPRITE_DRAW_HEIGHT = 190;
    private static final int TARGET_CHARACTER_HEIGHT = 145;
    static final int GROUND_Y = 360;
    private static final int BACKGROUND_COLOR_LIMIT = 0;
    private static final int MIN_VISIBLE_ALPHA = 32;
    private static final float MOVE_SPEED = 260f;
    private static final float SPRINT_SPEED = 430f;
    private static final float JUMP_SPEED = 520f;
    private static final float GRAVITY = 1450f;
    private static final int ATTACK_COMPONENT_MIN_PIXELS = 1000;
    private static final double ATTACK_FRAME_SCALE = 0.488;
    private static final int BLOCK_COMPONENT_MIN_PIXELS = 20_000;
    private static final int BLOCK_EFFECT_MAX_DISTANCE = 64;
    private static final double BLOCK_FRAME_SCALE = 0.38;

    private final BufferedImage[] idleFrames;
    private final BufferedImage[] runFrames;
    private final BufferedImage[] jumpStartFrames;
    private final BufferedImage[] jumpEndFrames;
    private final BufferedImage[] attackFrames;
    private final BufferedImage[] blockFrames;
    private int currentFrame = 0;
    private long lastFrameUpdate;
    private float x = 140;
    private float verticalOffset;
    private float verticalSpeed;
    private String state = "idle";
    private long stateTime = 0;
    private boolean facingLeft;
    private boolean running;
    private boolean sprinting;
    private boolean attacking;
    private long attackTransitionStart;
    private boolean attackTransition;
    private boolean attackFacingLeft;
    private boolean blocking;
    private boolean blockTransition;
    private boolean blockTransitionInto;
    private long blockTransitionStart;
    private BufferedImage blockTransitionFrom;
    private boolean blockFacingLeft;
    private JumpPhase jumpPhase = JumpPhase.NONE;

    private enum JumpPhase {
        NONE,
        START,
        AIRBORNE,
        END
    }

    public Player() {
        idleFrames = loadFrames("character/Idle/idle-sheet.png", IDLE_FRAME_COUNT, 0, -1);
        runFrames = loadFrames("character/Idle/Run/Run-Sheet.png", RUN_FRAME_COUNT, 0, -1);
        jumpStartFrames = loadFrames("character/Jump/Start/Jump-Start-Sheet.png",
                JUMP_START_FRAME_COUNT, 0, -1);
        jumpEndFrames = loadFrames("character/Jump/End/Jump-End-Sheet.png",
                JUMP_END_FRAME_COUNT, 0, -1);
        attackFrames = loadAttackFrames("character/Attack/Attack-Sheet.png");
        blockFrames = loadPoseFrames("character/Block/Block-Sheet.png", BLOCK_FRAME_COUNT,
                BLOCK_FRAME_SCALE, BLOCK_COMPONENT_MIN_PIXELS,
                BLOCK_EFFECT_MAX_DISTANCE, "block");
        lastFrameUpdate = System.nanoTime();
    }

    private BufferedImage[] loadAttackFrames(String path) {
        return loadPoseFrames(path, ATTACK_FRAME_COUNT, ATTACK_FRAME_SCALE,
                ATTACK_COMPONENT_MIN_PIXELS, 2, "attack");
    }

    private BufferedImage[] loadPoseFrames(String path, int frameCount, double frameScale,
            int minimumComponentPixels, int attachedPixelDistance, String animationName) {
        try {
            File imageFile = new File(path);
            if (!imageFile.exists()) {
                imageFile = new File("PANANAKOP/" + path);
            }
            if (!imageFile.exists()) {
                imageFile = new File("..\\PANANAKOP\\" + path);
            }
            if (!imageFile.exists()) {
                throw new IOException("Could not find sprite sheet: " + path);
            }

            BufferedImage spriteSheet = ImageIO.read(imageFile);
            if (spriteSheet == null) {
                throw new IOException("Could not read sprite sheet: " + imageFile.getPath());
            }

            BufferedImage[] frames = splitPoseFrames(
                    spriteSheet, frameCount, minimumComponentPixels,
                    attachedPixelDistance, animationName);
            double sharedScale = frameScale;
            for (BufferedImage frame : frames) {
                sharedScale = Math.min(sharedScale,
                        Math.min(SPRITE_DRAW_WIDTH / (double) frame.getWidth(),
                                SPRITE_DRAW_HEIGHT / (double) frame.getHeight()));
            }
            for (int i = 0; i < frames.length; i++) {
                frames[i] = normalizeFrame(frames[i], sharedScale);
            }
            return frames;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to load player " + animationName + " animation: " + path, e);
        }
    }

    private BufferedImage[] splitPoseFrames(BufferedImage spriteSheet, int frameCount,
            int minimumComponentPixels, int attachedPixelDistance, String animationName)
            throws IOException {
        int width = spriteSheet.getWidth();
        int height = spriteSheet.getHeight();
        int[] labels = new int[width * height];
        Arrays.fill(labels, -1);
        int[] queue = new int[labels.length];
        List<SpriteFrameRegion> regions = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int start = y * width + x;
                if (labels[start] != -1
                        || ((spriteSheet.getRGB(x, y) >>> 24) & 0xFF) < MIN_VISIBLE_ALPHA) {
                    continue;
                }

                int label = regions.size();
                int queueStart = 0;
                int queueEnd = 1;
                queue[0] = start;
                labels[start] = label;
                int minX = x;
                int maxX = x;
                int minY = y;
                int maxY = y;

                while (queueStart < queueEnd) {
                    int pixel = queue[queueStart++];
                    int pixelX = pixel % width;
                    int pixelY = pixel / width;
                    minX = Math.min(minX, pixelX);
                    maxX = Math.max(maxX, pixelX);
                    minY = Math.min(minY, pixelY);
                    maxY = Math.max(maxY, pixelY);

                    for (int offsetY = -1; offsetY <= 1; offsetY++) {
                        for (int offsetX = -1; offsetX <= 1; offsetX++) {
                            int neighborX = pixelX + offsetX;
                            int neighborY = pixelY + offsetY;
                            if (neighborX < 0 || neighborX >= width
                                    || neighborY < 0 || neighborY >= height) {
                                continue;
                            }

                            int neighbor = neighborY * width + neighborX;
                            if (labels[neighbor] == -1
                                    && ((spriteSheet.getRGB(neighborX, neighborY) >>> 24) & 0xFF)
                                            >= MIN_VISIBLE_ALPHA) {
                                labels[neighbor] = label;
                                queue[queueEnd++] = neighbor;
                            }
                        }
                    }
                }

                if (queueEnd >= minimumComponentPixels) {
                    regions.add(new SpriteFrameRegion(label, minX, minY, maxX, maxY));
                } else {
                    for (int i = 0; i < queueEnd; i++) {
                        labels[queue[i]] = -1;
                    }
                }
            }
        }

        if (regions.size() != frameCount) {
            throw new IOException("Expected " + frameCount + " " + animationName
                    + " poses but found " + regions.size() + ".");
        }

        regions.sort(Comparator.comparingInt((SpriteFrameRegion region) -> region.left));
        int[] sortedLabels = new int[regions.size()];
        for (int i = 0; i < regions.size(); i++) {
            sortedLabels[regions.get(i).label] = i;
        }

        int[] owners = new int[labels.length];
        Arrays.fill(owners, -1);
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] >= 0) {
                owners[i] = sortedLabels[labels[i]];
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int alpha = (spriteSheet.getRGB(x, y) >>> 24) & 0xFF;
                if (owners[index] != -1 || alpha == 0) {
                    continue;
                }
                int nearest = findNearestRegion(regions, x, y);
                SpriteFrameRegion region = regions.get(nearest);
                int dx = x < region.left ? region.left - x
                        : x > region.right ? x - region.right : 0;
                int dy = y < region.top ? region.top - y
                        : y > region.bottom ? y - region.bottom : 0;
                if (dx <= attachedPixelDistance && dy <= attachedPixelDistance) {
                    owners[index] = nearest;
                }
            }
        }

        int[] left = new int[regions.size()];
        int[] top = new int[regions.size()];
        int[] right = new int[regions.size()];
        int[] bottom = new int[regions.size()];
        Arrays.fill(left, width);
        Arrays.fill(top, height);
        Arrays.fill(right, -1);
        Arrays.fill(bottom, -1);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int owner = owners[y * width + x];
                if (owner >= 0) {
                    left[owner] = Math.min(left[owner], x);
                    top[owner] = Math.min(top[owner], y);
                    right[owner] = Math.max(right[owner], x);
                    bottom[owner] = Math.max(bottom[owner], y);
                }
            }
        }

        BufferedImage[] frames = new BufferedImage[regions.size()];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new BufferedImage(
                    right[i] - left[i] + 1, bottom[i] - top[i] + 1,
                    BufferedImage.TYPE_INT_ARGB);
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int owner = owners[y * width + x];
                if (owner >= 0) {
                    frames[owner].setRGB(
                            x - left[owner], y - top[owner], spriteSheet.getRGB(x, y));
                }
            }
        }
        return frames;
    }

    private int findNearestRegion(List<SpriteFrameRegion> regions, int x, int y) {
        int nearest = 0;
        long nearestDistance = Long.MAX_VALUE;
        for (int i = 0; i < regions.size(); i++) {
            SpriteFrameRegion region = regions.get(i);
            int dx = x < region.left ? region.left - x
                    : x > region.right ? x - region.right : 0;
            int dy = y < region.top ? region.top - y
                    : y > region.bottom ? y - region.bottom : 0;
            long distance = (long) dx * dx + (long) dy * dy;
            if (distance < nearestDistance) {
                nearest = i;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static class SpriteFrameRegion {
        private final int label;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private SpriteFrameRegion(int label, int left, int top, int right, int bottom) {
            this.label = label;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private BufferedImage[] loadFrames(String path, int frameCount, int cropTop, int cropHeight) {
        try {
            File imageFile = new File(path);
            if (!imageFile.exists()) {
                imageFile = new File("PANANAKOP/" + path);
            }
            if (!imageFile.exists()) {
                imageFile = new File("..\\PANANAKOP\\" + path);
            }

            if (!imageFile.exists()) {
                throw new IOException("Could not find sprite sheet: " + path);
            }

            BufferedImage spriteSheet = ImageIO.read(imageFile);
            if (spriteSheet == null) {
                throw new IOException("Could not read sprite sheet: " + imageFile.getPath());
            }

            int frameHeight = cropHeight < 0 ? spriteSheet.getHeight() : cropHeight;
            if (cropTop + frameHeight > spriteSheet.getHeight()) {
                throw new IOException("Animation crop is outside the sprite sheet: " + path);
            }

            BufferedImage[] frames = new BufferedImage[frameCount];
            int totalFrameHeight = 0;
            boolean hasOpaqueBackground = ((spriteSheet.getRGB(0, 0) >>> 24) & 0xFF) > 0;

            for (int i = 0; i < frameCount; i++) {
                int left = i * spriteSheet.getWidth() / frameCount;
                int right = (i + 1) * spriteSheet.getWidth() / frameCount;
                BufferedImage frame = new BufferedImage(
                        right - left, frameHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D frameGraphics = frame.createGraphics();
                frameGraphics.drawImage(
                        spriteSheet.getSubimage(left, cropTop, right - left, frameHeight),
                        0, 0, null);
                frameGraphics.dispose();
                frames[i] = frame;
                if (hasOpaqueBackground) {
                    removeBlackBackground(frames[i]);
                }
                frames[i] = trimTransparentEdges(frames[i]);
                totalFrameHeight += frames[i].getHeight();
            }

            for (int i = 0; i < frameCount; i++) {
                double scale = (double) TARGET_CHARACTER_HEIGHT * frameCount / totalFrameHeight;
                frames[i] = normalizeFrame(frames[i], scale);
            }
            return frames;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load player animation: " + path, e);
        }
    }

    private void removeBlackBackground(BufferedImage frame) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        boolean[] visited = new boolean[width * height];
        int[] queue = new int[width * height];
        int queueEnd = 0;

        for (int x = 0; x < width; x++) {
            queueEnd = addBlackPixel(frame, x, 0, visited, queue, queueEnd);
            queueEnd = addBlackPixel(frame, x, height - 1, visited, queue, queueEnd);
        }
        for (int y = 1; y < height - 1; y++) {
            queueEnd = addBlackPixel(frame, 0, y, visited, queue, queueEnd);
            queueEnd = addBlackPixel(frame, width - 1, y, visited, queue, queueEnd);
        }

        int queueStart = 0;
        while (queueStart < queueEnd) {
            int pixel = queue[queueStart++];
            int x = pixel % width;
            int y = pixel / width;
            frame.setRGB(x, y, 0x00000000);

            if (x > 0) {
                queueEnd = addBlackPixel(frame, x - 1, y, visited, queue, queueEnd);
            }
            if (x + 1 < width) {
                queueEnd = addBlackPixel(frame, x + 1, y, visited, queue, queueEnd);
            }
            if (y > 0) {
                queueEnd = addBlackPixel(frame, x, y - 1, visited, queue, queueEnd);
            }
            if (y + 1 < height) {
                queueEnd = addBlackPixel(frame, x, y + 1, visited, queue, queueEnd);
            }
        }
    }

    private int addBlackPixel(BufferedImage frame, int x, int y, boolean[] visited,
            int[] queue, int queueEnd) {
        int index = y * frame.getWidth() + x;
        int color = frame.getRGB(x, y);
        boolean blackBackground = ((color >>> 24) & 0xFF) > 0
                && ((color >>> 16) & 0xFF) <= BACKGROUND_COLOR_LIMIT
                && ((color >>> 8) & 0xFF) <= BACKGROUND_COLOR_LIMIT
                && (color & 0xFF) <= BACKGROUND_COLOR_LIMIT;
        if (!visited[index] && blackBackground) {
            visited[index] = true;
            queue[queueEnd++] = index;
        }
        return queueEnd;
    }

    private BufferedImage trimTransparentEdges(BufferedImage frame) {
        int left = frame.getWidth();
        int top = frame.getHeight();
        int right = -1;
        int bottom = -1;

        for (int x = 0; x < frame.getWidth(); x++) {
            for (int y = 0; y < frame.getHeight(); y++) {
                int alpha = (frame.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha >= MIN_VISIBLE_ALPHA) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                } else {
                    frame.setRGB(x, y, 0x00000000);
                }
            }
        }

        if (right < left || bottom < top) {
            throw new IllegalStateException("Player animation frame has no visible pixels.");
        }
        return frame.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }

    private BufferedImage normalizeFrame(BufferedImage frame, double scale) {
        int width = (int) Math.round(frame.getWidth() * scale);
        int height = (int) Math.round(frame.getHeight() * scale);
        if (width > SPRITE_DRAW_WIDTH || height > SPRITE_DRAW_HEIGHT) {
            throw new IllegalStateException("Player animation frame does not fit the shared canvas.");
        }
        BufferedImage normalizedFrame = new BufferedImage(
                SPRITE_DRAW_WIDTH, SPRITE_DRAW_HEIGHT, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = normalizedFrame.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(
                frame,
                (SPRITE_DRAW_WIDTH - width) / 2,
                SPRITE_DRAW_HEIGHT - height,
                width,
                height,
                null);
        graphics.dispose();

        return normalizedFrame;
    }

    public void update(double deltaSeconds, boolean movingLeft, boolean movingRight, int screenWidth) {
        update(deltaSeconds, movingLeft, movingRight, false, screenWidth);
    }

    public void update(double deltaSeconds, boolean movingLeft, boolean movingRight,
            boolean sprinting, int screenWidth) {
        long now = System.nanoTime();
        boolean canMove = !blocking && !(blockTransition && blockTransitionInto);
        boolean shouldRun = canMove && movingLeft != movingRight;
        boolean shouldSprint = shouldRun && sprinting;
        if (shouldSprint != this.sprinting) {
            lastFrameUpdate = now;
        }
        this.sprinting = shouldSprint;
        if (shouldRun != running && jumpPhase == JumpPhase.NONE && !attacking
                && !blocking && !blockTransition) {
            running = shouldRun;
            currentFrame = 0;
            lastFrameUpdate = now;
        } else {
            running = shouldRun;
        }

        if (shouldRun) {
            float speed = this.sprinting ? SPRINT_SPEED : MOVE_SPEED;
            x += (movingRight ? 1 : -1) * speed * deltaSeconds;
            if (!attacking && !attackTransition && !blocking && !blockTransition) {
                facingLeft = movingLeft;
            }
        }

        if (jumpPhase == JumpPhase.START || jumpPhase == JumpPhase.AIRBORNE) {
            verticalOffset += verticalSpeed * deltaSeconds;
            verticalSpeed += GRAVITY * deltaSeconds;
            if (verticalOffset >= 0 && verticalSpeed > 0) {
                verticalOffset = 0;
                verticalSpeed = 0;
                jumpPhase = JumpPhase.END;
                currentFrame = 0;
                lastFrameUpdate = now;
            }
        }

        updateAnimation(now);
        if (attackTransition && now - attackTransitionStart >= ATTACK_TRANSITION_TIME) {
            attackTransition = false;
        }
        if (blockTransition && now - blockTransitionStart >= BLOCK_TRANSITION_TIME) {
            blockTransition = false;
            blockTransitionFrom = null;
        }
        x = Math.max(0, Math.min(x, screenWidth - SPRITE_DRAW_WIDTH));

        if (!"idle".equals(state) && System.currentTimeMillis() - stateTime >= 180) {
            state = "idle";
        }
    }

    private void updateAnimation(long now) {
        long frameTime;
        int frameCount;

        if (attacking) {
            frameTime = ATTACK_FRAME_TIME;
            frameCount = attackFrames.length;
        } else if (blocking) {
            frameTime = BLOCK_FRAME_TIME;
            frameCount = blockFrames.length;
        } else if (jumpPhase == JumpPhase.START) {
            frameTime = JUMP_FRAME_TIME;
            frameCount = jumpStartFrames.length;
        } else if (jumpPhase == JumpPhase.END) {
            frameTime = JUMP_FRAME_TIME;
            frameCount = jumpEndFrames.length;
        } else if (jumpPhase == JumpPhase.AIRBORNE) {
            return;
        } else {
            frameTime = running ? (sprinting ? SPRINT_FRAME_TIME : RUN_FRAME_TIME)
                    : IDLE_FRAME_TIME;
            frameCount = running ? runFrames.length : idleFrames.length;
        }

        long framesToAdvance = (now - lastFrameUpdate) / frameTime;
        if (framesToAdvance <= 0) {
            return;
        }

        if (attacking) {
            if (currentFrame + framesToAdvance >= frameCount) {
                attacking = false;
                attackTransition = true;
                attackTransitionStart = now;
                currentFrame = 0;
                lastFrameUpdate = now;
            } else {
                currentFrame += (int) framesToAdvance;
            }
            lastFrameUpdate = now;
        } else if (blocking) {
            currentFrame = Math.min(frameCount - 1, currentFrame + (int) framesToAdvance);
            lastFrameUpdate += framesToAdvance * frameTime;
        } else if (jumpPhase == JumpPhase.START) {
            if (currentFrame + framesToAdvance >= frameCount) {
                jumpPhase = JumpPhase.AIRBORNE;
                currentFrame = frameCount - 1;
            } else {
                currentFrame += (int) framesToAdvance;
            }
            lastFrameUpdate = now;
        } else if (jumpPhase == JumpPhase.END) {
            if (currentFrame + framesToAdvance >= frameCount) {
                jumpPhase = JumpPhase.NONE;
                currentFrame = 0;
            } else {
                currentFrame += (int) framesToAdvance;
            }
            lastFrameUpdate = now;
        } else {
            currentFrame = (currentFrame + (int) (framesToAdvance % frameCount)) % frameCount;
            lastFrameUpdate += framesToAdvance * frameTime;
        }
    }

    public void jump() {
        if (jumpPhase != JumpPhase.NONE || attacking || blocking || blockTransition) {
            return;
        }
        jumpPhase = JumpPhase.START;
        currentFrame = 0;
        lastFrameUpdate = System.nanoTime();
        verticalOffset = 0;
        verticalSpeed = -JUMP_SPEED;
    }

    public void attack() {
        if (attacking || jumpPhase != JumpPhase.NONE || blocking || blockTransition) {
            return;
        }
        attacking = true;
        attackTransition = false;
        attackFacingLeft = facingLeft;
        currentFrame = 0;
        lastFrameUpdate = System.nanoTime();
        state = "attack";
        stateTime = System.currentTimeMillis();
    }

    public void block() {
        if (blocking || attacking || attackTransition || jumpPhase != JumpPhase.NONE) {
            return;
        }
        long now = System.nanoTime();
        blockTransitionFrom = getDisplayedFrame(now);
        blocking = true;
        blockFacingLeft = facingLeft;
        blockTransition = true;
        blockTransitionInto = true;
        blockTransitionStart = now;
        currentFrame = 0;
        lastFrameUpdate = now;
    }

    public void unblock() {
        if (!blocking) {
            return;
        }
        long now = System.nanoTime();
        blockTransitionFrom = getDisplayedFrame(now);
        blocking = false;
        blockTransition = true;
        blockTransitionInto = false;
        blockTransitionStart = now;
        currentFrame = 0;
        lastFrameUpdate = now;
    }

    public void draw(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        long now = System.nanoTime();
        BufferedImage frame = getDisplayedFrame(now);
        int drawY = Math.round(GROUND_Y - SPRITE_DRAW_HEIGHT + verticalOffset);
        if (attacking) {
            drawFrame(g2d, frame, 1f, attackFacingLeft, drawY);
        } else if (attackTransition) {
            float elapsed = Math.min(1f, (now - attackTransitionStart)
                    / (float) ATTACK_TRANSITION_TIME);
            float progress = elapsed * elapsed * (3f - 2f * elapsed);
            drawFrame(g2d, blendFrames(
                    attackFrames[attackFrames.length - 1], frame, progress),
                    1f, attackFacingLeft, drawY);
        } else {
            drawFrame(g2d, frame, 1f,
                    blocking || blockTransition ? blockFacingLeft : facingLeft, drawY);
        }

        g2d.dispose();
    }

    private BufferedImage getDisplayedFrame(long now) {
        if (!blockTransition) {
            return getCurrentFrame();
        }
        float elapsed = Math.min(1f,
                (now - blockTransitionStart) / (float) BLOCK_TRANSITION_TIME);
        float progress = elapsed * elapsed * (3f - 2f * elapsed);
        BufferedImage target = blockTransitionInto ? blockFrames[currentFrame] : getLocomotionFrame();
        return blendFrames(blockTransitionFrom, target, progress);
    }

    private BufferedImage blendFrames(BufferedImage from, BufferedImage to, float progress) {
        int[] fromPixels = from.getRGB(
                0, 0, SPRITE_DRAW_WIDTH, SPRITE_DRAW_HEIGHT, null, 0, SPRITE_DRAW_WIDTH);
        int[] toPixels = to.getRGB(
                0, 0, SPRITE_DRAW_WIDTH, SPRITE_DRAW_HEIGHT, null, 0, SPRITE_DRAW_WIDTH);
        int[] blendedPixels = new int[fromPixels.length];
        float fromWeight = 1f - progress;

        for (int i = 0; i < blendedPixels.length; i++) {
            int fromPixel = fromPixels[i];
            int toPixel = toPixels[i];
            float fromAlpha = ((fromPixel >>> 24) & 0xFF) / 255f;
            float toAlpha = ((toPixel >>> 24) & 0xFF) / 255f;
            float alpha = fromAlpha * fromWeight + toAlpha * progress;
            if (alpha == 0f) {
                continue;
            }

            int red = blendChannel(fromPixel >>> 16, toPixel >>> 16,
                    fromAlpha, toAlpha, fromWeight, progress, alpha);
            int green = blendChannel(fromPixel >>> 8, toPixel >>> 8,
                    fromAlpha, toAlpha, fromWeight, progress, alpha);
            int blue = blendChannel(fromPixel, toPixel,
                    fromAlpha, toAlpha, fromWeight, progress, alpha);
            int outputAlpha = Math.round(alpha * 255f);
            blendedPixels[i] = (outputAlpha << 24) | (red << 16) | (green << 8) | blue;
        }

        BufferedImage blendedFrame = new BufferedImage(
                SPRITE_DRAW_WIDTH, SPRITE_DRAW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        blendedFrame.setRGB(
                0, 0, SPRITE_DRAW_WIDTH, SPRITE_DRAW_HEIGHT,
                blendedPixels, 0, SPRITE_DRAW_WIDTH);
        return blendedFrame;
    }

    private int blendChannel(int fromPixel, int toPixel, float fromAlpha, float toAlpha,
            float fromWeight, float toWeight, float outputAlpha) {
        float fromChannel = fromPixel & 0xFF;
        float toChannel = toPixel & 0xFF;
        float blended = (fromChannel * fromAlpha * fromWeight
                + toChannel * toAlpha * toWeight) / outputAlpha;
        return Math.round(blended);
    }

    private void drawFrame(Graphics2D g, BufferedImage frame, float alpha,
            boolean drawFacingLeft, int drawY) {
        if (alpha <= 0f) {
            return;
        }

        Graphics2D frameGraphics = (Graphics2D) g.create();
        frameGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        if (drawFacingLeft) {
            frameGraphics.drawImage(frame, Math.round(x) + SPRITE_DRAW_WIDTH, drawY,
                    Math.round(x), drawY + SPRITE_DRAW_HEIGHT,
                    0, 0, frame.getWidth(), frame.getHeight(), null);
        } else {
            frameGraphics.drawImage(frame, Math.round(x), drawY,
                    SPRITE_DRAW_WIDTH, SPRITE_DRAW_HEIGHT, null);
        }
        frameGraphics.dispose();
    }

    private BufferedImage getCurrentFrame() {
        if (attacking) {
            return attackFrames[currentFrame];
        }
        if (blocking) {
            return blockFrames[currentFrame];
        }
        return getLocomotionFrame();
    }

    private BufferedImage getLocomotionFrame() {
        if (jumpPhase == JumpPhase.START) {
            return jumpStartFrames[currentFrame];
        }
        if (jumpPhase == JumpPhase.AIRBORNE) {
            return jumpStartFrames[jumpStartFrames.length - 1];
        }
        if (jumpPhase == JumpPhase.END) {
            return jumpEndFrames[currentFrame];
        }
        return running ? runFrames[currentFrame] : idleFrames[currentFrame];
    }
}