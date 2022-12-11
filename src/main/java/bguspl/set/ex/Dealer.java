package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UtilImpl;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Random;

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

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
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
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        int curId = 0;
        for(int i = 0 ; i < players.length ; i++){
            if(players[i].getPickedSlots().size() == 3)
                curId = i;
        }
        List<Integer> OptionalSet = players[curId].getPickedSlots();
        //find the set where the player clicked , check if it is legal and remove it
        int[] set = new int[3];
        set[0] = table.slotToCard[OptionalSet.get(0)];
        set[1] = table.slotToCard[OptionalSet.get(1)];
        set[2] = table.slotToCard[OptionalSet.get(2)];
        /*if(env.util.testSet(set))*/
        if(true){
            env.ui.removeTokens();
            players[curId].resetSlots();
            table.removeCard(table.cardToSlot[set[0]]);
            table.removeCard(table.cardToSlot[set[1]]);
            table.removeCard(table.cardToSlot[set[2]]);
            players[curId].penalty();
            players[curId].point();
        }
        else {
            players[curId].penalty();
            players[curId].penalty();
            players[curId].penalty();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        Random random = new Random();
        for (int j = 0; j < 12; j++) {
            if(table.slotToCard[j] == null && deck.size() > 0) { // check if removed , need to check if player clicked it
                int randomCard = 0;
                if(deck.size() != 1) {
                    randomCard = random.nextInt(deck.size() - 1); // check if size - 1 or size
                }
                table.placeCard(deck.get(randomCard), j);
                deck.remove(randomCard);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        //env.ui.setCountdown(reshuffleTime, false);
        //env.ui.
        reshuffleTime = System.currentTimeMillis() + 3000;
        players[0].run();

        //players[1].run();
        /*if(players[0].getPickedSlots().size() == 3)
            removeCardsFromTable();*/
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        reshuffleTime = System.currentTimeMillis() + 3000;

        env.ui.setElapsed(reshuffleTime - System.currentTimeMillis());
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        for(Integer card : table.slotToCard){
            deck.add(card);
            table.removeCard(table.cardToSlot[card]);
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
}
