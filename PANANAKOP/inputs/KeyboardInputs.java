package inputs;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;

import pananakop.GamePanel;

public class KeyboardInputs implements KeyListener {

    private final GamePanel gamePanel;
    private final Set<Integer> movementKeys = new HashSet<>();
    private boolean spaceDown;
    private boolean sprintDown;

    public KeyboardInputs(GamePanel gamePanel) {
        this.gamePanel = gamePanel;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_A || keyCode == KeyEvent.VK_LEFT) {
            movementKeys.add(keyCode);
        } else if (keyCode == KeyEvent.VK_D || keyCode == KeyEvent.VK_RIGHT) {
            movementKeys.add(keyCode);
        } else if (keyCode == KeyEvent.VK_SPACE && !spaceDown) {
            spaceDown = true;
            gamePanel.jump();
        } else if (keyCode == KeyEvent.VK_SHIFT) {
            sprintDown = true;
        }
        updateMovement();
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        movementKeys.remove(keyCode);
        if (keyCode == KeyEvent.VK_SPACE) {
            spaceDown = false;
        } else if (keyCode == KeyEvent.VK_SHIFT) {
            sprintDown = false;
        }
        updateMovement();
    }

    private void updateMovement() {
        gamePanel.setMovingLeft(
                movementKeys.contains(KeyEvent.VK_A) || movementKeys.contains(KeyEvent.VK_LEFT));
        gamePanel.setMovingRight(
                movementKeys.contains(KeyEvent.VK_D) || movementKeys.contains(KeyEvent.VK_RIGHT));
        gamePanel.setSprinting(sprintDown);
    }
}
