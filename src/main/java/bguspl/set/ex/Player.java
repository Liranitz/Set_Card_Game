package bguspl.set.ex;
import java.util.*;

import bguspl.set.Env;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.concurrent.CopyOnWriteArrayList;

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
     * The penalty that the player need to get.
     */
    private int penalty;

    public volatile boolean wait;

    //the cardId
    private ConcurrentLinkedQueue<Integer> curSlots;
    /**
     * The current score of the player.
     */

    private ReentrantLock curLocker;
    private ReentrantLock curLocker2;
    private ReentrantLock curLocker3;

    private CopyOnWriteArrayList<Integer> pickedSlots;
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
        this.pickedSlots = new CopyOnWriteArrayList<Integer>();
        this.dealer = dealer;
        this.curSlots = new ConcurrentLinkedQueue<>();
        this.penalty = 0;
        this.wait = true;
        curLocker = new ReentrantLock();
        curLocker2 = new ReentrantLock();
        curLocker3 = new ReentrantLock();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            // TODO implement main player loop
            synchronized (this) {
                try {
                    if (!wait) {
                        updateTokens();
                        penalty();
                    } else {
                        this.wait();
                    }
                }
                catch (InterruptedException ignored){}
            }
        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    public void needToWait(boolean con) {
        wait = con;
/*        synchronized (this) {
            if (con) {
                wait = con;
                curLocker.lock();
            } else {
                wait = con;
                curLocker.unlock();
                //tifyAll();
            }
        }*/
    }

    public boolean  isHuman(){
        return human;
    }
    
    public void resetTokensSlots(int[] set) {
        env.ui.removeTokens(set[0]);
        env.ui.removeTokens(set[1]);
        env.ui.removeTokens(set[2]);
    }

        public synchronized void updateTokens() {
        curLocker.lock();
                try {
                    while (!curSlots.isEmpty()) {
                        Integer cardSlot = curSlots.poll(); // gets the CARD ID
                        if (table.cardToSlot[cardSlot] != null) {//the card still exist on the table
                            int temp = -1;
                            for (int j = 0; j < pickedSlots.size(); j++) {
                                if (pickedSlots.get(j) == cardSlot) // checked if the player clicked on that CARD
                                    temp = j;
                            }
                            if (temp != -1) {//the player want ro remove the pick of the card
                                table.removeToken(id, table.cardToSlot[cardSlot]);
                                pickedSlots.remove(temp);
                            } else if (pickedSlots.size() < 3) {//not exist in player pickedSlots, so add it.
                                table.placeToken(this.id, table.cardToSlot[cardSlot]);
                                pickedSlots.add(cardSlot);
                                if (pickedSlots.size() == 3) {//an optional SlotSet that need to be checked
                                    CopyOnWriteArrayList<Integer> slotToSend = new CopyOnWriteArrayList<>();
                                    for (Integer i : pickedSlots) {
                                        slotToSend.add(i);
                                    }
                                    dealer.putInSet(slotToSend, id);//id - recognize which player the set belongs
                                    wait = true;
                                    dealer.wait = false; // calls the dealer to wake up
                                }
                                // here the set.size can be 2 instead of 3 (the player removed one)_
                            }
                        }
                    }
                } finally {
                    curLocker.unlock();
                }

        }



    public void resetSlots(){
        this.pickedSlots.clear();
    }

    public void updateSlots(CopyOnWriteArrayList<Integer> set) {
        for (int i : set) {
            for (int j = 0; j < pickedSlots.size(); j++) {
                if (i == pickedSlots.get(j))
                    pickedSlots.remove(j);
            }
        }
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
                // use choose random and send keyPressed
                if (!wait) {
                    chooseRandomAi();
                } else {
                    try {
                        synchronized (this) {
                            wait();
                        }
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        } , "computer-" + id);
        aiThread.start();
    }

    private void chooseRandomAi(){
        boolean filter = true;
        if(pickedSlots.size() == 3){
            filter = false;
        }
        if(table.countCards() > 0) {
            ArrayList<Integer> optionalSlots1 = new ArrayList<>();
            //  checks if there are already someone that inside my slot[]
            for (Integer i : table.slotToCard) {
                if (i != null) {
                    if (filter) {
                        if (!pickedSlots.contains(i)) {
                            optionalSlots1.add(i);
                        }
                    }
                    else {
                        optionalSlots1.add(i);
                    }
                }
            }
            if(optionalSlots1.size() > 0){
                Random rand = new Random();
                int chosenRandom = rand.nextInt(optionalSlots1.size());
                keyPressed(table.cardToSlot[optionalSlots1.get(chosenRandom)]);
            }
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        //Thread.currentThread().interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        //if (penalty == 0 && pickedSlots.size() < 3){
            if (penalty == 0 && table.slotToCard[slot] != null){
            curSlots.add(table.slotToCard[slot]);
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {
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
     * set the penalty
     */
    public void setPenalty(int n) {
        this.penalty = n;
    }
    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        //sleep after legal set
            if (penalty == 1) {
                try {
                    long freeze = env.config.pointFreezeMillis + System.currentTimeMillis();
                    while (dealer.timeIsRun && freeze - System.currentTimeMillis() > env.config.pointFreezeMillis / 2) {
                        env.ui.setFreeze(this.id, freeze - System.currentTimeMillis());
                        Thread.sleep((long) (env.config.pointFreezeMillis / 3 * 0.98));
                    }
                    if (dealer.timeIsRun && freeze - System.currentTimeMillis()>0)
                        Thread.sleep(freeze - System.currentTimeMillis());
                    env.ui.setFreeze(this.id, 0);
                    penalty = 0;
                } catch (InterruptedException ignored) {
                }
            }

        //sleep after illegal set
        if(penalty == 2){
            try {
                env.ui.setFreeze(this.id,  env.config.penaltyFreezeMillis);
                long freeze = env.config.penaltyFreezeMillis + System.currentTimeMillis();
                while (dealer.timeIsRun && (freeze - System.currentTimeMillis()) > env.config.penaltyFreezeMillis / 6){
                    env.ui.setFreeze(this.id, freeze - System.currentTimeMillis());
                    Thread.sleep((long) (env.config.penaltyFreezeMillis / 10 * 0.98));
                }
                if (dealer.timeIsRun && freeze - System.currentTimeMillis()>0)
                    Thread.sleep(freeze - System.currentTimeMillis());
                env.ui.setFreeze(this.id, 0);
                if(!human){
                    //deleteRandomSlot();
                }
                penalty = 0;
            } catch (InterruptedException ignored) {}
        }

    }


    public int getScore() {
        return score;
    }
}
