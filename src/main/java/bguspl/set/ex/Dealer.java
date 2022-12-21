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
    private long timeSinceLastAction;

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

    public volatile ConcurrentLinkedQueue<Integer> CuncurrentSets2;
    private Thread[] playerThreads;
    private Thread dealerThread;
    public volatile boolean wait;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        dealerThread = Thread.currentThread();
        CuncurrentSets2 = new ConcurrentLinkedQueue<>();
        wait = false;
        curLocker = new ReentrantLock();
        CurElapsed = 0;
        timeIsRun = false;
        playerThreads = new Thread[players.length];
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        //making thread to each player and start them
        for (int i = 0 ; i < players.length ; i++){
                playerThreads[i] = new Thread(players[i] , "Player ID : " + i);
                playerThreads[i].start();
        }

        try {
            while (!shouldFinish()) {
                    for (Player p : players) // sleep all the players until dealer finish put on cards
                        synchronized (p) {
                            p.wait = true;
                        }
                    placeCardsOnTable();
                    for (Player p : players) { // wake them up
                        synchronized (p) {
                            p.wait = false;
                            p.notifyAll();
                        }
                }
                //reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                if (env.config.turnTimeoutMillis>0)
                    reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                else
                    timeSinceLastAction = System.currentTimeMillis();

                timerLoop();
                updateTimerDisplay(false);
                removeAllCardsFromTable();
            }
        } catch (InterruptedException ignored) {}
        announceWinners();
        terminate();
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

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        for (int i = players.length - 1; i >= 0; i--) {
                players[i].terminate();
            try {
                    playerThreads[i].interrupt();
                    playerThreads[i].join();
                }
                 catch (InterruptedException ignored) {
                }
        }
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
            synchronized (table) {
                if (!this.CuncurrentSets2.isEmpty()) {
                    Integer curId = CuncurrentSets2.poll();
                    synchronized (players[curId]) {
                        players[curId].wait = true;
                    }
                        Set<Integer> OptionalSet = players[curId].pickedSlots;
                        Integer[] CurOptionalArray = new Integer[LEGAL_SET_LENGTH];
                        int j = 0;
                        for (Integer i : OptionalSet) {
                            CurOptionalArray[j] = i;
                            j++;
                        }
                        if (checkIfStillExist(CurOptionalArray)) { // check if the set is exist on the table
                            //find the set where the player clicked , check if it is legal and remove it
                            int[] set = new int[LEGAL_SET_LENGTH];
                            int notLegalSet = 0;
                            for (int i = 0; i < LEGAL_SET_LENGTH; i++) {
                                if (CurOptionalArray[i] != null) {
                                    set[i] = CurOptionalArray[i];
                                    notLegalSet++;
                                }
                            }
                             if (notLegalSet == LEGAL_SET_LENGTH && env.util.testSet(set)) {
                                //delete the all token from the places were there is a set
                                for (int i = 0; i < LEGAL_SET_LENGTH; i++) {
                                    System.out.print("   " + set[i] + " from " + curId + " , ");
                                    if (table.cardToSlot[set[i]] != null) {
                                        set[i] = table.cardToSlot[set[i]]; //******************************************
                                        table.removeCard(set[i]);
                                    }
                                }
                                players[curId].curSlots.clear();
                                players[curId].pickedSlots.clear();
                                for (Player p : players) {//update the other player pickeslots
                                    if (p.id != curId) {
                                        synchronized (p) {
                                            p.updateSlots(CurOptionalArray);
                                        }
                                    }
                                }

                                players[curId].penalty = players[curId].FREEZE_POINT;
                                 if (env.config.turnTimeoutMillis > 0)
                                     reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                                 else if (env.config.turnTimeoutMillis == 0)
                                     timeSinceLastAction = System.currentTimeMillis();
                                //reshuffleTime = (long) (System.currentTimeMillis() + env.config.turnTimeoutMillis * 1.01);
                            } else { //not a legal set
                                players[curId].penalty = players[curId].FREEZE_PENALTY;
                            }
                        } else { // update the player's set with the cards that deleted from table
                            //synchronized (players[curId]) {
                                players[curId].updateSlots(CurOptionalArray);
                            //}
                        }

                    synchronized (players[curId]) {
                        players[curId].wait = false;
                        players[curId].notifyAll();
                    }
                }
            }
        }

    public boolean checkIfStillExist(Integer[] OptionalSet) {
        boolean con = true;
        if(OptionalSet.length != 3)
            return false;
        for (int i = 0; i < OptionalSet.length-1; i++) {
            if (OptionalSet[i] != null) {
                if (table.cardToSlot[OptionalSet[i]] == null) {
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
        synchronized (table) {
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

            /*if (env.config.turnTimeoutMillis == 0){ //need to check there is a legal set in the table
                List<Integer> tableCardList = new ArrayList<>();
                for (int i=0; i<table.slotToCard.length; i++)
                    tableCardList.add(i);
                List<int[]> sets = env.util.findSets(tableCardList, 1);
                if (sets.size() == 0){
                    removeAllCardsFromTable();
                    placeCardsOnTable();
                }
            }*/
        }
    }








    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() throws InterruptedException {
        // TODO implement

            if(CurElapsed > 0 || wait) {
                this.wait(env.config.turnTimeoutMillis / 600); // is that ok?
            }
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

/*        CurElapsed = System.currentTimeMillis();
        //env.ui.setElapsed(reshuffleTime - CurElapsed);
        env.ui.setCountdown(reshuffleTime - CurElapsed, reset);*/
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        synchronized (table) {
            CuncurrentSets2.clear();
            for (Player p : players) {
                synchronized (p) {
                p.wait = true;
                p.pickedSlots.clear();
                p.penalty = p.NO_NEED_TO_FREEZE;
                }
            }
            for (Integer card : table.slotToCard) {
                if (card != null) {
                    deck.add(card);
                    table.removeCard(table.cardToSlot[card]);
                }
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
        terminate();
    }
}
