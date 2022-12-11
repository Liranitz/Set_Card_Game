package bguspl.set.ex;
import java.util.*;

import bguspl.set.Env;
import java.util.logging.Level;
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

    private Dealer dealer;

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
        this.dealer = dealer;
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
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        //need to check !!

        while (!terminate) {
            int i=0;
            // TODO implement main player loop
            try {
                playerThread.join();
                synchronized (this) { wait(); }
            } catch (InterruptedException ignored) {}

            //table.countCards()
            //dealer.reshuffleTime = System.currentTimeMillis() + 60000;
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    public void resetSlots(){
        this.pickedSlots = new ArrayList<>();
    }

    public void deleteSlots(List<Integer> set){
        //try {
            //synchronized (this) {
                for (int i : set) {
                    for (int j = 0; j < pickedSlots.size(); j++) {
                        if (i == pickedSlots.get(j))
                            pickedSlots.remove(j);
                    }
                }


            //}

        //}
        //catch(ConcurrentModificationException e) {} // needs to do something with the exception?
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator

                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    private ArrayList<Integer> aiPickedSlots(){
        return null;

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

        if (table.slotToCard[slot] != null){
            int temp = -1;
            for (int i=0; i<pickedSlots.size(); i++)
                if (pickedSlots.get(i) == slot)
                    temp = i;
            if (temp != -1){
                table.removeToken(id, slot);
                pickedSlots.remove(temp);
            }

            else if (pickedSlots.size() < 3) {
                table.placeToken(this.id, slot);
                pickedSlots.add(slot);
                if(pickedSlots.size()==3) {
                    this.pickedSlots.add(0 , id);
                    try {
                        synchronized (this) {
                            dealer.putInSet(this.pickedSlots);
                        }
                    }
                    catch (Exception e) {};
                }
                /*if(pickedSlots.size() == 3){
                    //this.playerThread.interrupt();
                    this.playerThread.notifyAll();
                }*/
            }
        }


    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        this.score++;
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        //sleep for 1 second.
        try {
            synchronized (this) {
                terminate = false;
                long freeze = env.config.pointFreezeMillis + System.currentTimeMillis();
                env.ui.setFreeze(this.id, env.config.pointFreezeMillis);
                Thread.sleep(env.config.pointFreezeMillis);
                while (System.currentTimeMillis() <= freeze) {

                }
                env.ui.setFreeze(this.id, 0);
                terminate = true;
            }
        }
        catch (Exception e) {}
    }

    public int getScore() {
        return score;
    }
}
