package pananakop;


import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import inputs.KeyboardInputs;
import inputs.MouseInputs;

public class GamePanel extends JPanel {

    private MouseInputs mouseInputs;
    private Player player;
    private final BufferedImage backgroundImage;
    private float xDelta = 100, yDelta = 100;
    private boolean movingLeft;
    private boolean movingRight;
    private boolean sprinting;
    private long lastUpdateTime;

    public GamePanel() {
        player = new Player();
        backgroundImage = loadBackgroundImage();
        mouseInputs = new MouseInputs(this);
        setPanelSize();
        setFocusable(true);
        addKeyListener(new KeyboardInputs(this));
        addMouseListener(mouseInputs);
        addMouseMotionListener(mouseInputs);
    }

    private BufferedImage loadBackgroundImage() {
        String path = "Background/Background.png";
        File imageFile = new File(path);
        if (!imageFile.exists()) {
            imageFile = new File("PANANAKOP/" + path);
        }
        if (!imageFile.exists()) {
            imageFile = new File("..\\PANANAKOP\\Background\\Background.png");
        }

        if (!imageFile.exists()) {
            throw new IllegalStateException("Could not find game background: " + path);
        }

        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                throw new IOException("Could not read image: " + imageFile.getPath());
            }
            return image;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load game background: " + path, e);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        requestFocusInWindow();
    }

    private void setPanelSize() {
        Dimension size = new Dimension(1280, 800);

        setMinimumSize(size);
        setMaximumSize(size);
        setPreferredSize(size);
    }

    public void primaryAction() {
        player.attack();
    }

    public void jump() {
        player.jump();
    }

    public void attack() {
        player.attack();
    }

    public void block() {
        player.block();
    }

    public void unblock() {
        player.unblock();
    }

    public void setMovingLeft(boolean movingLeft) {
        this.movingLeft = movingLeft;
    }

    public void setMovingRight(boolean movingRight) {
        this.movingRight = movingRight;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public void changexDelta(int delta) {
        xDelta += delta;
    }

    public void changeyDelta(int delta) {
        yDelta += delta;
    }

    public void setRectpostion(int x, int y) {
        xDelta = x;
        yDelta = y;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
        g.setColor(new Color(91, 67, 45));
        g.fillRect(0, Player.GROUND_Y, getWidth(), getHeight() - Player.GROUND_Y);
        g.setColor(new Color(103, 133, 63));
        g.fillRect(0, Player.GROUND_Y, getWidth(), 6);

        long now = System.nanoTime();
        double deltaSeconds = lastUpdateTime == 0 ? 0 : (now - lastUpdateTime) / 1_000_000_000.0;
        lastUpdateTime = now;
        player.update(deltaSeconds, movingLeft, movingRight, sprinting, getWidth());
        player.draw(g);
    }
}