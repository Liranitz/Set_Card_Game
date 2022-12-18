package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
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

    private long CurElapsed;

    private long timeSinceLastAction;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    public volatile long reshuffleTime = Long.MAX_VALUE;

    int setSize;

    public volatile boolean timeIsRun;
    private Queue<CopyOnWriteArrayList<Integer>> CuncurrentSets;

    private Thread dealerThread;

    public volatile boolean wait;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        dealerThread = Thread.currentThread();
        CuncurrentSets = new ConcurrentLinkedQueue<>();
        wait = false;
        curLocker = new ReentrantLock();
        CurElapsed = 0;
        timeIsRun = false;
        setSize = 3;
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
        while (!shouldFinish()) {
            /*for(Player p : players) // sleep all the players until dealer finish put on cards
                synchronized (p) {
                    p.wait = true;
                }*/
            placeCardsOnTable();
            for(Player p : players) // wake them up
                synchronized (p) {
                    p.wait = false;
                    p.notifyAll();
                }
            if (env.config.turnTimeoutMillis>0)
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            else
                timeSinceLastAction = System.currentTimeMillis();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime && env.util.findSets(deck, 1).size() > 0) {
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
            if (!this.CuncurrentSets.isEmpty()) {
                CopyOnWriteArrayList<Integer> OptionalSet = CuncurrentSets.poll();
                System.out.println(OptionalSet);
                int curId = OptionalSet.remove(3); //gets the playerId
                if (checkIfStillExist(OptionalSet)) { // check if the set is exist on the table
                    //find the set where the player clicked , check if it is legal and remove it
                    int[] set = new int[setSize];
                    set[0] = OptionalSet.get(0);
                    set[1] = OptionalSet.get(1);
                    set[2] = OptionalSet.get(2);
                    if (env.util.testSet(set)) {
                   // if (true) {
                        //delete the all token from the places were there is a set
                        set[0] = table.cardToSlot[set[0]];
                        set[1] = table.cardToSlot[set[1]];
                        set[2] = table.cardToSlot[set[2]];
                        players[curId].resetTokensSlots(set);
                        env.ui.removeTokens(set[0]);
                        env.ui.removeTokens(set[1]);
                        env.ui.removeTokens(set[2]);
                        players[curId].resetSlots();//reset the player pickedSlots
                        for (Player p : players) {//update the other player pickeslots
                            if (p.id != curId)
                                p.updateSlots(OptionalSet);
                        }
                        //remove the card of the set from the table
                        table.removeCard(set[0]);
                        table.removeCard(set[1]);
                        table.removeCard(set[2]);
                        players[curId].setPenalty(1);
                        players[curId].point();
                        if (env.config.turnTimeoutMillis > 0)
                            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                        else if (env.config.turnTimeoutMillis == 0)
                            timeSinceLastAction = System.currentTimeMillis();
                        // add a deck check
                    } else { //not a legal set
                        players[curId].setPenalty(2);
                    }
                } else { // update the player's set with the cards that deleted from table
                    players[curId].updateSlots(OptionalSet);
                }
                synchronized (players[curId]) {
                    players[curId].wait = false;
                    players[curId].notifyAll();
                }
            }
        }
         finally {
            curLocker.unlock();
        }
    }

    public boolean checkIfStillExist(List<Integer> OptionalSet) {
        boolean con = true;
        for (int i = 0; i < OptionalSet.size() ; i++) {
            if (table.cardToSlot[OptionalSet.get(i)] == null) {
                OptionalSet.remove(i);
                con = false;
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
        if (env.config.turnTimeoutMillis == 0){ //need to check there is a legal set in the table
            List<Integer> tableCardList = new ArrayList<>();
            for (int i=0; i<table.slotToCard.length; i++)
                tableCardList.add(i);
            List<int[]> sets = env.util.findSets(tableCardList, 1);
            if (sets.size() == 0){
                removeAllCardsFromTable();
                placeCardsOnTable();
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
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        try{
            if(CurElapsed > 0) {
                Thread.sleep(env.config.tableDelayMillis); //
            }
        } catch (InterruptedException interruptedException) {}
    }
    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (env.config.turnTimeoutMillis>0){
            CurElapsed = System.currentTimeMillis();
            //env.ui.setElapsed(reshuffleTime - CurElapsed);
            env.ui.setCountdown(reshuffleTime - CurElapsed, reset);
        }

        if (env.config.turnTimeoutMillis == 0){
            CurElapsed = System.currentTimeMillis();
            //env.ui.setElapsed(reshuffleTime - CurElapsed);
            env.ui.setCountdown(  CurElapsed - timeSinceLastAction , reset);
        }

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

    public void putInSet(CopyOnWriteArrayList<Integer> setSlot , Integer Id){
        // pun in the optional set with a lock
/*        synchronized (players[Id]) {
            players[Id].wait = true;*/
            setSlot.add(Id);
            this.CuncurrentSets.add(setSlot);
        //}
    }
}
