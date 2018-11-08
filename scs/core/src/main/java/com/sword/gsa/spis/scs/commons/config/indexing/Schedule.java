package com.sword.gsa.spis.scs.commons.config.indexing;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Schedule {

	public static final long NOT_EFFECTIVE_NOW = -650L;
	public static final long NEVER_EFFECTIVE = -651L;
	public static final long ALWAYS_EFFECTIVE = -652L;

	public final List<Period> periods = new ArrayList<>();

	/**
	 * @return How long the period of this schedule that will be in effect the longest, will remain in effect. Returns {@link Schedule#ALWAYS_EFFECTIVE} if the at least one period is always effective,
	 *         Returns {@link Schedule#NOT_EFFECTIVE_NOW} if no period is not effective right now.
	 */
	public long howLongWillRemainEffective(final long nowMillis) {
		long howLongWillRemainEffective = NOT_EFFECTIVE_NOW;
		long tmp = NOT_EFFECTIVE_NOW;
		for (final Period p : periods) {
			tmp = p.howLongWillRemainEffective(nowMillis);
			if (tmp == ALWAYS_EFFECTIVE) return ALWAYS_EFFECTIVE;
			else if (tmp > howLongWillRemainEffective) howLongWillRemainEffective = tmp;
		}
		return howLongWillRemainEffective;
	}

	/**
	 * @return <code>nowMillis</code> if one of this schedule's period is already in effect, {@link Schedule#NEVER_EFFECTIVE} if no period ever applies, the next time the next period to apply will be
	 *         in effect otherwise.
	 */
	public long getNextTimeWillBeInEffect(final long nowMillis) {
		if (periods.isEmpty()) return NEVER_EFFECTIVE;

		final List<Long> nextTimes = new ArrayList<>();
		long tmp;
		for (final Period p : periods) {
			tmp = p.getNextTimeWillBeInEffect(nowMillis);
			if (tmp != NEVER_EFFECTIVE) nextTimes.add(new Long(tmp));
		}

		if (nextTimes.isEmpty()) return NEVER_EFFECTIVE;
		else {
			Collections.sort(nextTimes);
			return nextTimes.get(0).longValue();
		}
	}

	public static class Period {

		public static final int ALL_DAYS = -1;
		public static final int WEEK_DAYS = -2;

		public final int day;
		public final int startHour;
		public final int duration;
		private final boolean alwaysEffective;

		/**
		 * @param day
		 *            The {@link Calendar#DAY_OF_WEEK}
		 * @param startHour
		 *            The start time (see {@link Calendar#HOUR_OF_DAY})
		 * @param duration
		 *            The number of hours this period lasts
		 */
		public Period(final int day, final int startHour, final int duration) {
			super();
			this.day = day;
			this.startHour = startHour;
			this.duration = duration;
			alwaysEffective = day == ALL_DAYS && duration > 23 || duration > 7 * 24 - 1;
		}

		/**
		 * @return How long the period will remain in effect. Returns {@link Schedule#ALWAYS_EFFECTIVE} if the period is always effective, Returns {@link Schedule#NOT_EFFECTIVE_NOW} if the period is
		 *         not effective right now.
		 */
		public long howLongWillRemainEffective(final long nowMillis) {

			if (alwaysEffective) return Schedule.ALWAYS_EFFECTIVE;

			final long durationMillis = TimeUnit.HOURS.toMillis(duration);

			final Calendar now = Calendar.getInstance();
			now.setTimeInMillis(nowMillis);
			final int curWeekOfYear = now.get(Calendar.WEEK_OF_YEAR);

			int[] daysToCheck = null;
			if (day == ALL_DAYS) daysToCheck = new int[] {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};
			else if (day == WEEK_DAYS) daysToCheck = new int[] {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY};
			else daysToCheck = new int[] {day};

			for (final int dayToCheck : daysToCheck) {

				final Calendar periodStart = Calendar.getInstance();
				periodStart.setTimeInMillis(nowMillis);
				periodStart.set(Calendar.DAY_OF_WEEK, dayToCheck);
				periodStart.set(Calendar.HOUR_OF_DAY, startHour);
				periodStart.set(Calendar.SECOND, 0);
				periodStart.set(Calendar.MINUTE, 0);
				periodStart.set(Calendar.MILLISECOND, 0);

				long periodStartMillis = periodStart.getTimeInMillis();
				if (nowMillis > periodStartMillis && nowMillis < periodStartMillis + durationMillis) return periodStartMillis + durationMillis - nowMillis;

				periodStart.clear();
				periodStart.setTimeInMillis(nowMillis);
				periodStart.set(Calendar.DAY_OF_WEEK, dayToCheck);
				periodStart.set(Calendar.HOUR_OF_DAY, startHour);
				periodStart.set(Calendar.SECOND, 0);
				periodStart.set(Calendar.MINUTE, 0);
				periodStart.set(Calendar.MILLISECOND, 0);
				periodStart.set(Calendar.WEEK_OF_YEAR, curWeekOfYear - 1);// Check one week ago too in case current DAY_OF_WEEK < this.day
				periodStartMillis = periodStart.getTimeInMillis();
				if (nowMillis > periodStartMillis && nowMillis < periodStartMillis + durationMillis) return periodStartMillis + durationMillis - nowMillis;

			}

			return Schedule.NOT_EFFECTIVE_NOW;

		}

		/**
		 * @return <code>nowMillis</code> if period is already in effect, {@link Schedule#NEVER_EFFECTIVE} if period never applies, the next time this period will be in effect otherwise.
		 */
		public long getNextTimeWillBeInEffect(final long nowMillis) {

			final long howLongWillRemainEffective = howLongWillRemainEffective(nowMillis);

			if (howLongWillRemainEffective == Schedule.NOT_EFFECTIVE_NOW) {

				final Calendar now = Calendar.getInstance();
				now.setTimeInMillis(nowMillis);
				final int curWeekOfYear = now.get(Calendar.WEEK_OF_YEAR);

				int[] daysToCheck = null;
				if (day == ALL_DAYS) daysToCheck = new int[] {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};
				else if (day == WEEK_DAYS) daysToCheck = new int[] {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY};
				else daysToCheck = new int[] {day};

				final List<Long> nextTimes = new ArrayList<>();

				for (final int dayToCheck : daysToCheck) {

					final Calendar periodStart = Calendar.getInstance();
					periodStart.setTimeInMillis(nowMillis);
					periodStart.set(Calendar.DAY_OF_WEEK, dayToCheck);
					periodStart.set(Calendar.HOUR_OF_DAY, startHour);
					periodStart.set(Calendar.SECOND, 0);
					periodStart.set(Calendar.MINUTE, 0);
					periodStart.set(Calendar.MILLISECOND, 0);

					long periodStartMillis = periodStart.getTimeInMillis();
					if (periodStartMillis > nowMillis) nextTimes.add(new Long(periodStartMillis));

					periodStart.clear();
					periodStart.setTimeInMillis(nowMillis);
					periodStart.set(Calendar.DAY_OF_WEEK, dayToCheck);
					periodStart.set(Calendar.HOUR_OF_DAY, startHour);
					periodStart.set(Calendar.SECOND, 0);
					periodStart.set(Calendar.MINUTE, 0);
					periodStart.set(Calendar.MILLISECOND, 0);
					periodStart.set(Calendar.WEEK_OF_YEAR, curWeekOfYear + 1);// Check one week later too in case current DAY_OF_WEEK > this.day
					periodStartMillis = periodStart.getTimeInMillis();
					if (periodStartMillis > nowMillis) nextTimes.add(new Long(periodStartMillis));

				}

				if (nextTimes.isEmpty()) return Schedule.NEVER_EFFECTIVE;
				else {
					Collections.sort(nextTimes);
					return nextTimes.get(0).longValue();
				}

			} else return nowMillis;
		}

	}

}
