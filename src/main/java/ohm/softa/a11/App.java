package ohm.softa.a11;

import ohm.softa.a11.openmensa.OpenMensaAPI;
import ohm.softa.a11.openmensa.OpenMensaAPIService;
import ohm.softa.a11.openmensa.model.Canteen;
import ohm.softa.a11.openmensa.model.Meal;
import ohm.softa.a11.openmensa.model.PageInfo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

/**
 * @author Peter Kurfer
 * Created on 12/16/17.
 */
public class App {
	private static final String OPEN_MENSA_DATE_FORMAT = "yyyy-MM-dd";

	private static final SimpleDateFormat dateFormat = new SimpleDateFormat(OPEN_MENSA_DATE_FORMAT, Locale.getDefault());
	private static final Scanner inputScanner = new Scanner(System.in);
	private static final OpenMensaAPI openMensaAPI = OpenMensaAPIService.getInstance().getOpenMensaAPI();
	private static final Calendar currentDate = Calendar.getInstance();
	private static int currentCanteenId = -1;

	public static void main(String[] args) throws ExecutionException, InterruptedException {
		MenuSelection selection;
		/* loop while true to get back to the menu every time an action was performed */
		do {
			selection = menu();
			switch (selection) {
				case SHOW_CANTEENS:
					printCanteens();
					break;
				case SET_CANTEEN:
					readCanteen();
					break;
				case SHOW_MEALS:
					printMeals();
					break;
				case SET_DATE:
					readDate();
					break;
				case QUIT:
					System.exit(0);

			}
		} while (true);
	}

	private static void printCanteens() throws ExecutionException, InterruptedException {
		System.out.print("Fetching canteens [");
		openMensaAPI.getCanteens()
			.thenApply(response -> {
				System.out.print("#");
				PageInfo pageInfo = PageInfo.extractFromResponse(response);
				List<Canteen> allCanteens;

				if (response.body() == null) {
					allCanteens = new LinkedList<>();
				} else {
					allCanteens = response.body();
				}

				CompletableFuture<List<Canteen>> remainingCanteensFuture = null;

				for (int i = 2; i <= pageInfo.getTotalCountOfPages(); i++) {
					System.out.print("#");
					if (remainingCanteensFuture == null) {
						remainingCanteensFuture = openMensaAPI.getCanteens(i);
					} else {
						remainingCanteensFuture = remainingCanteensFuture.thenCombine(openMensaAPI.getCanteens(i), (l1, l2) -> {
							List<Canteen> result = new LinkedList<>(l1);
							result.addAll(l2);
							return result;
						});
					}
				}

				try {
					allCanteens.addAll(remainingCanteensFuture.get());
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
				System.out.println("]");
				allCanteens.sort(Comparator.comparing(Canteen::getId));
				return allCanteens;
			})
			.thenAccept(canteens -> {
				for (Canteen c : canteens) {
					System.out.println(c);
				}
			})
			.get();
	}
	private static void printMeals() throws ExecutionException, InterruptedException {
		if (currentCanteenId < 0) {
			System.out.println("No canteen selected!");
			return;
		}

		final String dateString = dateFormat.format(currentDate.getTime());

		openMensaAPI.getCanteenState(currentCanteenId, dateString)
			.thenApply(state -> {
				if (state != null && !state.isClosed()) {
					try {
						return openMensaAPI.getMeals(currentCanteenId, dateString).get();
					} catch (InterruptedException | ExecutionException e) {
					}
				} else {
					System.out.println(String.format("Seems like the canteen has closed on this date: %s", dateFormat.format(currentDate.getTime())));
				}
				return new LinkedList<Meal>();
			})
			.thenAccept(meals -> {
				for (Meal m : meals) {
					System.out.println(m);
				}
			})
			.get(); /* block the thread by calling `get` to ensure that all results are retrieved when the method is completed */
	}

	/**
	 * Utility method to select a canteen
	 */
	private static void readCanteen() {
		/* typical input reading pattern */
		boolean readCanteenId = false;
		do {
			try {
				System.out.println("Enter canteen id:");
				currentCanteenId = inputScanner.nextInt();
				readCanteenId = true;
			} catch (Exception e) {
				System.out.println("Sorry could not read the canteen id");
			}
		} while (!readCanteenId);
	}

	/**
	 * Utility method to read a date and update the calendar
	 */
	private static void readDate() {
		/* typical input reading pattern */
		boolean readDate = false;
		do {
			try {
				System.out.println("Pleae enter date in the format yyyy-mm-dd:");
				Date d = dateFormat.parse(inputScanner.next());
				currentDate.setTime(d);
				readDate = true;
			} catch (ParseException p) {
				System.out.println("Sorry, the entered date could not be parsed.");
			}
		} while (!readDate);

	}

	/**
	 * Utility method to print menu and read the user selection
	 *
	 * @return user selection as MenuSelection
	 */
	private static MenuSelection menu() {
		IntStream.range(0, 20).forEach(i -> System.out.print("#"));
		System.out.println();
		System.out.println("1) Show canteens");
		System.out.println("2) Set canteen");
		System.out.println("3) Show meals");
		System.out.println("4) Set date");
		System.out.println("5) Quit");
		IntStream.range(0, 20).forEach(i -> System.out.print("#"));
		System.out.println();

		switch (inputScanner.nextInt()) {
			case 1:
				return MenuSelection.SHOW_CANTEENS;
			case 2:
				return MenuSelection.SET_CANTEEN;
			case 3:
				return MenuSelection.SHOW_MEALS;
			case 4:
				return MenuSelection.SET_DATE;
			default:
				return MenuSelection.QUIT;
		}
	}
}
