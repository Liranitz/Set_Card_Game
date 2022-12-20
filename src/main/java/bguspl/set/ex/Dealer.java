package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.spi.CurrencyNameProvider;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Random;
import java.util.Queue;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    private ReentrantLock curLocker;
    private final int PLAYER_ID_INDEX = 3;
    private final int LEGAL_SET_LENGTH = 3;
    private long CurElapsed;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    public volatile long reshuffleTime = Long.MAX_VALUE;

    public volatile boolean timeIsRun;
    public volatile ConcurrentLinkedQueue<Set<Integer>> CuncurrentSets;
    public volatile ConcurrentLinkedQueue<Integer> CuncurrentSets2;
    private Thread dealerThread;

    public volatile boolean wait;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        dealerThread = Thread.currentThread();
        CuncurrentSets = new ConcurrentLinkedQueue<>();
        CuncurrentSets2 = new ConcurrentLinkedQueue<>();
        wait = false;
        curLocker = new ReentrantLock();
        CurElapsed = 0;
        timeIsRun = false;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        //making thread to each player and start them
        for (Player p : players) {
            Thread playerThread = new Thread(p);
            playerThread.start();
        }

        //check if need to join them??

        try {
            while (!shouldFinish()) {
                for(Player p : players) // sleep all the players until dealer finish put on cards
                    synchronized (p) {
                        p.wait = true;
                    }
                placeCardsOnTable();
                for(Player p : players) { // wake them up
                    synchronized (p) {
                        p.wait = false;
                        p.notifyAll();
                    }
                }
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                timerLoop();
                updateTimerDisplay(false);
                removeAllCardsFromTable();
            }
        } catch (InterruptedException ignored) {}
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() throws InterruptedException {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {

            timeIsRun = true;
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
        timeIsRun = false;
    }

/*    public void needToWait(boolean con) {
        if (!con) {
            synchronized (this) {
                notifyAll();
            }
        }
        wait = con;
    }*/

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        for(Player p : players)
            synchronized (p) {
                p.terminate();
            }
       terminate = true;
        //Thread.currentThread().interrupt();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }//need to check the cards on the table or on the deck??????????????


    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        curLocker.lock();
        try {
            synchronized (table) {
                if (!this.CuncurrentSets2.isEmpty()) {
                    Integer curId = CuncurrentSets2.poll();
                    synchronized (players[curId]) {
                        players[curId].wait = true;
                    }
                /*if (!this.CuncurrentSets.isEmpty()) {
                    Set<Integer> OptionalSet = CuncurrentSets.poll();*/
                    Set<Integer> OptionalSet = players[curId].pickedSlots;
                    //int curId = 1000; //gets the playerId
                    //Integer itemToRemove = 0;
                    Integer[] CurOptionalArray = new Integer[LEGAL_SET_LENGTH];
                    int j = 0;
                    for (Integer i : OptionalSet) {
                        //if (i >= 1000) {
                            //itemToRemove = i;
                       // } else {
                            CurOptionalArray[j] = i;
                            j++;
                        //}
                    }
                   // curId = itemToRemove - curId;
                    //OptionalSet.remove(itemToRemove);

                    if (checkIfStillExist(CurOptionalArray)) { // check if the set is exist on the table
                        //find the set where the player clicked , check if it is legal and remove it
                        int[] set = new int[LEGAL_SET_LENGTH];
                        for (int i = 0; i < LEGAL_SET_LENGTH; i++) {
                            if (CurOptionalArray[i] != null)
                                set[i] = CurOptionalArray[i];
                        }
                        if (env.util.testSet(set)) {
                            //if (true) {
                            //delete the all token from the places were there is a set
                            for (int i = 0; i < LEGAL_SET_LENGTH; i++) {
                                System.out.print("   " + set[i] + " from " + curId+ " , ");
                                set[i] = table.cardToSlot[set[i]]; //******************************************
                            }
                            System.out.println("");
                            players[curId].resetTokensSlots(set);
                        /*for(int i = 0 ; i < LEGAL_SET_LENGTH ; i++) // already happens
                            env.ui.removeTokens(set[i]);
                        */
                            players[curId].resetSlots();//reset the player pickedSlots
                            for (Player p : players) {//update the other player pickeslots
                                synchronized (p) {
                                    if (p.id != curId)
                                        p.updateSlots(CurOptionalArray);
                                }
                            }
                            //remove the card of the set from the table
                            for (int i = 0; i < LEGAL_SET_LENGTH; i++) // already happens
                                table.removeCard(set[i]);
                            players[curId].penalty = players[curId].FREEZE_POINT;
                            reshuffleTime = (long) (System.currentTimeMillis() + env.config.turnTimeoutMillis * 1.01);
                            // add a deck check
                        } else { //not a legal set
                            players[curId].penalty = players[curId].FREEZE_PENALTY;
                        }
                    } else { // update the player's set with the cards that deleted from table
                        //synchronized (players[curId]) {
                            players[curId].updateSlots(CurOptionalArray);
                    }
                    synchronized (players[curId]) {
                        players[curId].wait = false;
                        players[curId].notifyAll();
                    }
                }
            }
        }
         finally {
            curLocker.unlock();
        }
    }

    public boolean checkIfStillExist(Integer[] OptionalSet) {
        boolean con = true;
        for (int i = 0; i < OptionalSet.length-1; i++) {
            if (OptionalSet[i] != null) {
                if (table.cardToSlot[OptionalSet[i]].equals(null)) {
                    OptionalSet[i] = null;
                    con = false;
                }
            }
        }
        return con;
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        /*for (Player p : players) {//dont play while i am updating the table
            p.wait = true;
        }*/
        Random random = new Random();
        for (int j = 0; j < 12; j++) {
            if (table.slotToCard[j] == null && deck.size() > 0) { // check if removed , need to check if player clicked it
                int randomCard = 0;
                if (deck.size() != 1) {
                    randomCard = random.nextInt(deck.size() - 1); // check if size - 1 or size
                }
                table.placeCard(deck.get(randomCard), j);
                deck.remove(randomCard);
            }
        }
       /*for (Player p : players) {
           synchronized (p) {
               p.wait = false;
               p.notifyAll();
           }
        }*/
    }








    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() throws InterruptedException {
        // TODO implement

            if(CurElapsed > 0 || wait) {
                this.wait(env.config.turnTimeoutMillis / 600); // is that ok?
                //Thread.wait(100); //
            }


    }
    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        CurElapsed = System.currentTimeMillis();
        //env.ui.setElapsed(reshuffleTime - CurElapsed);
        env.ui.setCountdown(reshuffleTime - CurElapsed, reset);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        CuncurrentSets.clear();
        for(Player p : players){
            synchronized (p) {
                p.resetSlots();
                p.wait = true;
                p.setPenalty(0);
            }
        }
        for(Integer card : table.slotToCard){
            if(card != null) {
                deck.add(card);
                table.removeCard(table.cardToSlot[card]);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int max = 0;
        List<Integer> playersWon = new LinkedList<>();
        for(int i = 0 ; i < players.length ; i++){
            if(max < players[i].getScore())
                max = players[i].getScore();
        }
        for(int i = 0 ; i < players.length ; i++){
            if(max == players[i].getScore()) {
                playersWon.add(i);
            }
        }
        int[] playerIntWon = new int[playersWon.size()];
        for(int i = 0 ; i < playersWon.size() ; i++){
            playerIntWon[i] = playersWon.get(i);
        }
        env.ui.announceWinner(playerIntWon);
    }

/*    public void putInSet(CopyOnWriteArrayList<Integer> setSlot , Integer Id) {
        // pun in the optional set with a lock
*//*        synchronized (players[Id]) {
            players[Id].wait = true;*//*
        //option1 - slotToSend in player , here is option 2
        synchronized (players[Id]) {
            players[Id].wait = true;
        }
        CopyOnWriteArrayList<Integer> slotToSend = new CopyOnWriteArrayList<>();
        for (Integer i : setSlot) {
            slotToSend.add(i);
        }
        slotToSend.add(Id);
        this.CuncurrentSets.add(slotToSend);
        //}
    }*/
}
