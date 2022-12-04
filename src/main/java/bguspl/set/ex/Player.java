package bguspl.set.ex;
import java.util.ArrayList;
import java.util.Scanner;
import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;


    /**
     * The current score of the player.
     */
    private ArrayList<Integer> pickedSlots;
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.pickedSlots = new ArrayList<>();
    }

    public  ArrayList<Integer> getPickedSlots(){
        return  pickedSlots;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        if (!human) createArtificialIntelligence();

        //need to check !!
        terminate = false;
        while (!terminate) {
            // TODO implement main player loop
            Scanner token = new Scanner(System.in);
            String i = token.nextLine();
            int slot = parserInput(i.charAt(0));
            keyPressed(slot);
            if (pickedSlots.size() == 3)
                terminate = true;
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * parse the player input to a number of slot
     */
    public int parserInput(char ch) {
        int pressKey=-1;
        if (ch == 'Q' || ch == 'U')
            pressKey = 0;
        else if (ch == 'W' || ch == 'I')
            pressKey = 1;
        else if (ch == 'E' || ch == 'O')
            pressKey = 2;
        else if (ch == 'R' || ch == 'P')
            pressKey = 3;
        else if (ch == 'A' || ch == 'J')
            pressKey = 4;
        else if (ch == 'S' || ch == 'K')
            pressKey = 5;
        else if (ch == 'D' || ch == 'L')
            pressKey = 6;
        else if (ch == 'F' || ch == ';')
            pressKey = 7;
        else if (ch == 'Z' || ch == 'M')
            pressKey = 8;
        else if (ch == 'X' || ch == ',')
            pressKey = 9;
        else if (ch == 'C' || ch == '.')
            pressKey = 10;
        else if (ch == 'V' || ch == '/')
            pressKey = 11;

        return pressKey;

    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                // TODO implement player key press simulator
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        env.ui.placeToken(this.id, slot);
        pickedSlots.add(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
    }

    public int getScore() {
        return score;
    }
}
