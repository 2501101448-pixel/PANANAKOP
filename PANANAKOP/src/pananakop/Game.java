package pananakop;

import java.util.concurrent.locks.LockSupport;


public class Game implements Runnable {
private GameWindow gameWindow;
private GamePanel gamePanel;
private Thread gameThread;
private final int FPS_SET = 120;

public Game() {
    gamePanel = new GamePanel();
    gameWindow = new GameWindow(gamePanel);

    startGameLoop();

}

 private void startGameLoop() {
        gameThread = new Thread(this);
        gameThread.start();
    }
 @Override 
    public void run() {

        // Game loop 
        double timePerFrame = 1000000000.0 / FPS_SET;
        long lastFrameTime = System.nanoTime();
        int frames = 0;
        long lastcheck = System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted()) {
            long now = System.nanoTime();
            long elapsed = now - lastFrameTime;
            if (elapsed >= timePerFrame) {
                gamePanel.repaint();
                lastFrameTime = now;
                frames++;
            } else {
                LockSupport.parkNanos((long) (timePerFrame - elapsed));
            }

            if (System.currentTimeMillis() - lastcheck >= 1000) {
                lastcheck = System.currentTimeMillis();
                System.out.println("FPS: " + frames);
                frames = 0;
            }
        }
    }

}