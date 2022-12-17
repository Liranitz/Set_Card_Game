package bguspl.set.ex;
import java.util.*;

import bguspl.set.Env;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;
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
     * The penalty that the player need to get.
     */
    private int penalty;

    private boolean wait;

    //the cardId
    private Queue<Integer> curSlots;
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
        this.curSlots = new ConcurrentLinkedDeque<>();
        this.penalty = 0;
        this.wait = true;
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
            ReentrantLock lock = new ReentrantLock();
            if(!wait){
                updateTokens();
                penalty();
            }

        }
        /*if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {*/
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    public void needToWait(boolean con){
        if(con) {
            synchronized (this) {
                notifyAll();
            }
        }
        wait = con;
    }

    public boolean  isHuman(){
        return human;
    }
    
    public synchronized void resetTokensSlots(int[] set) {//need to be synchronized?????????????
        /*env.ui.removeTokens(table.cardToSlot[set[0]]);
        env.ui.removeTokens(table.cardToSlot[set[1]]);
        env.ui.removeTokens(table.cardToSlot[set[2]]);
*/
        env.ui.removeTokens(set[0]);
        env.ui.removeTokens(set[1]);
        env.ui.removeTokens(set[2]);

    }

        public synchronized void updateTokens() {
            ReentrantLock curLocker = new ReentrantLock();
            synchronized (this) {
                //this.notifyAll();
                curLocker.lock();
                try {
                    while (!curSlots.isEmpty()) {
                        Integer cardSlot = curSlots.poll();
                        if (table.slotToCard[cardSlot] != null) {//the slot still exsist on the table
                            int temp = -1;
                            for (int j = 0; j < pickedSlots.size(); j++) {
                                if (pickedSlots.get(j) == cardSlot)
                                    temp = j;
                            }
                            if (temp != -1) {//the player want ro remove the pick of the card
                                //table.removeToken(id, table.cardToSlot[cardSlot]);
                                table.removeToken(id, cardSlot);
                                pickedSlots.remove(temp);
                            }
                            else if (pickedSlots.size() < 3) {//not exist in player pickedSlots, so add it.
                                //table.placeToken(this.id, table.cardToSlot[cardSlot]);
                                table.placeToken(this.id, cardSlot);
                                pickedSlots.add(cardSlot);
                                //pickedSlots.add(table.slotToCard[cardSlot]);
                                if (pickedSlots.size() == 3) {//an optional SlotSet that need to be checked
                                    this.pickedSlots.add(id);//to recognize which player the set belongs
                                    dealer.putInSet(pickedSlots);
                                /*synchronized (dealer) {
                                    //dealer.notifyAll();
                                    curLocker.lock();
                                    try {
                                        dealer.putInSet(pickedSlots);
                                    } finally {
                                        curLocker.unlock();
                                    }
                                }*/
                                }
                            }
                        }
                    }
                }
                catch (Exception e){}
                finally {
                    curLocker.unlock();
                }
            }
        }
    


    public void resetSlots(){
        for (Integer i : pickedSlots)
            if (table.cardToSlot[i] != null)
                env.ui.removeToken(id, table.cardToSlot[i]);
        this.pickedSlots.clear();


    }

    public void updateSlots(int[] set){
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
/*
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
        aiThread.start();*/

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

                if (!wait && pickedSlots.size() < 3) {//why curSlots<3????????????
                    chooseRandomAi();
                }
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        } , "computer-" + id);
        aiThread.start();
    }

    private void chooseRandomAi(){
        if(table.countCards() > 0) {
            int[] optionalSlots = new int[table.countCards()];
            ArrayList<Integer> optionalSlots1 = new ArrayList<>();
            int j = 0;
            //  checks if there are already someone that inside my slot[]
            for (Integer i : table.slotToCard) {
                if(i != null && !pickedSlots.contains(table.cardToSlot[i])) {
                    //optionalSlots[j] = i;
                    optionalSlots1.add(i);
                    //j++;
                }
            }
            if(optionalSlots.length > 0){
                Random rand = new Random();
                int chosenRandom = rand.nextInt(optionalSlots1.size());
                keyPressed(table.cardToSlot[optionalSlots1.get(chosenRandom)]);
            }
        }
    }

    private ArrayList<Integer> aiPickedSlots(){
        return pickedSlots;
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
        if (penalty == 0 && pickedSlots.size() < 3){
            curSlots.add(slot);
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
        //sleep for 1 second after legal set
            if (penalty == 1) {
                try {
                    long freeze = env.config.pointFreezeMillis + System.currentTimeMillis();
                    while (freeze - System.currentTimeMillis() > 500) {
                        env.ui.setFreeze(this.id, freeze - System.currentTimeMillis());
                        Thread.sleep(980);
                    }
                    if (freeze - System.currentTimeMillis()>0)
                        Thread.sleep(freeze - System.currentTimeMillis());
                    env.ui.setFreeze(this.id, 0);
                    penalty = 0;
                } catch (InterruptedException ignored) {
                }
            }

        //sleep for 3 second after illegal set
        if(penalty == 2){
            try {
                env.ui.setFreeze(this.id,  env.config.penaltyFreezeMillis);
                long freeze = env.config.penaltyFreezeMillis + System.currentTimeMillis();
                while (freeze - System.currentTimeMillis() > 1){
                    env.ui.setFreeze(this.id, freeze - System.currentTimeMillis());
                    Thread.sleep(env.config.penaltyFreezeMillis / 2);
                }
                if (freeze - System.currentTimeMillis()>0)
                Thread.sleep(freeze - System.currentTimeMillis());
                env.ui.setFreeze(this.id, 0);
                /*if (!isHuman()){//nee to change the non human cards
                    resetSlots();
                }*/
                if(!human){
                    deleteRandomSlot();
                }
                penalty = 0;
            } catch (InterruptedException ignored) {}
        }

    }

    private void deleteRandomSlot(){
        Random ran = new Random();
        int x = ran.nextInt(3);
        Integer remove = pickedSlots.remove(x);
        table.removeToken(id, remove);
    }


    public int getScore() {
        return score;
    }
}
