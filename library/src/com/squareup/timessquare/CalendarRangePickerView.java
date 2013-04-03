package com.squareup.timessquare;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static java.util.Calendar.DATE;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.SUNDAY;
import static java.util.Calendar.YEAR;

/**
 * Android component to allow picking a date from a calendar view (a list of months).  Must be
 * initialized after inflation with {@link #init(java.util.Date, java.util.Date, java.util.Date)}.
 * The currently selected date can be retrieved with {@link #getSelectedDate()}.
 */
public class CalendarRangePickerView extends ListView
    implements MonthView.Listener {

  public interface Listener {
    void onRangeStarted();
    void onRangeCompleted();
  }

  private Listener listener;
  private final CalendarRangePickerView.MonthAdapter adapter;
  private final DateFormat monthNameFormat;
  private final DateFormat weekdayNameFormat;
  private final DateFormat fullDateFormat;
  final List<MonthDescriptor> months = new ArrayList<MonthDescriptor>();
  final List<List<List<MonthCellDescriptor>>> cells =
      new ArrayList<List<List<MonthCellDescriptor>>>();

  private List<MonthCellDescriptor> selectedCells = new ArrayList<MonthCellDescriptor>();
  final Calendar today = Calendar.getInstance();
  private final Calendar selectedStartCal = Calendar.getInstance();
  private final Calendar selectedEndCal = Calendar.getInstance();
  private final Calendar minCal = Calendar.getInstance();
  private final Calendar maxCal = Calendar.getInstance();

  public CalendarRangePickerView(Context context, AttributeSet attrs) {
    super(context, attrs);
    adapter = new MonthAdapter();
    setAdapter(adapter);
    final int bg = getResources().getColor(R.color.calendar_bg);
    setBackgroundColor(bg);
    setCacheColorHint(bg);
    monthNameFormat = new SimpleDateFormat(context.getString(R.string.month_name_format));
    weekdayNameFormat = new SimpleDateFormat(context.getString(R.string.day_name_format));
    fullDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
  }

  protected int getMonthResourceId() {
    return R.layout.month;
  }

  public void init(Date minDate, Date maxDate) {
    init(null, null, minDate, maxDate);
  }

  /**
   * All date parameters must be non-null and their {@link java.util.Date#getTime()} must not return
   * 0.  Time of day will be ignored.  For instance, if you pass in {@code minDate} as 11/16/2012
   * 5:15pm and {@code maxDate} as 11/16/2013 4:30am, 11/16/2012 will be the first selectable date
   * and 11/15/2013 will be the last selectable date ({@code maxDate} is exclusive).
   *
   * @param selectedStartDate Earliest initially selected date, inclusive.  Must
   * be between {@code minDate} and {@code maxDate}.
   * @param selectedEndDate Latest initially selected date, inclusive.  Must
   * be between {@code minDate} and {@code maxDate}.
   * @param minDate Earliest selectable date, inclusive.  Must be earlier than {@code maxDate}.
   * @param maxDate Latest selectable date, exclusive.  Must be later than {@code minDate}.
   */
  public void init(Date selectedStartDate, Date selectedEndDate, Date minDate, Date maxDate) {
    if (minDate == null || maxDate == null) {
      throw new IllegalArgumentException("Min/max dates must be non-null");
    }
    if (minDate.getTime() == 0 || maxDate.getTime() == 0) {
      throw new IllegalArgumentException("Min/max dates must be non-zero");
    }
    if (minDate.after(maxDate)) {
      throw new IllegalArgumentException("Min date must be before max date");
    }

    // Clear previous state.
    cells.clear();
    months.clear();

    // Sanitize input: clear out the hours/minutes/seconds/millis.
    minCal.setTime(minDate);
    maxCal.setTime(maxDate);
    setMidnight(minCal);
    setMidnight(maxCal);
    // maxDate is exclusive: bump back to the previous day so if maxDate is the first of a month,
    // we don't accidentally include that month in the view.
    maxCal.add(MINUTE, -1);

    // Validate initial range.  Validation must happen after we set minCal/maxCal
    validateRange(selectedStartDate, selectedEndDate);

    if (selectedStartDate == null) {
      selectedStartCal.setTimeInMillis(0);
    } else {
      selectedStartCal.setTime(selectedStartDate);
      setMidnight(selectedStartCal);
    }

    if (selectedEndDate == null) {
      selectedEndCal.setTimeInMillis(0);
    } else {
      selectedEndCal.setTime(selectedEndDate);
      setMidnight(selectedEndCal);
    }

    // Now iterate between minCal and maxCal and build up our list of months to show.
    final Calendar monthCounter = Calendar.getInstance();

    monthCounter.setTime(minCal.getTime());
    final int maxMonth = maxCal.get(MONTH);
    final int maxYear = maxCal.get(YEAR);
    while ((monthCounter.get(MONTH) <= maxMonth // Up to, including the month.
        || monthCounter.get(YEAR) < maxYear) // Up to the year.
        && monthCounter.get(YEAR) < maxYear + 1) { // But not > next yr.
      MonthDescriptor month = new MonthDescriptor(monthCounter.get(MONTH), monthCounter.get(YEAR),
          monthNameFormat.format(monthCounter.getTime()));
      cells.add(getMonthCells(month, monthCounter, selectedStartCal, selectedEndCal));
      Logr.d("Adding month %s", month);
      months.add(month);
      monthCounter.add(MONTH, 1);
    }
    adapter.notifyDataSetChanged();
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  private void validateRange(Date startDate, Date endDate) {
    if ((startDate == null) && (endDate != null)) {
      throw new IllegalArgumentException("No end date without a start date");
    }

    if (startDate != null && !betweenDates(startDate, minCal, maxCal)) {
      throw new IllegalArgumentException("Start date out of range");
    }
    if (endDate != null && !betweenDates(endDate, minCal, maxCal)) {
      throw new IllegalArgumentException("End date out of range");
    }

    if (endDate != null && startDate.after(endDate)) {
      throw new IllegalArgumentException("Start date must be before end date");
    }
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (months.isEmpty()) {
      throw new IllegalStateException(
          "Must have at least one month to display.  Did you forget to call init()?");
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  public Date getSelectedStartDate() {
    if (selectedStartCal.getTimeInMillis() == 0) {
      return null;
    } else {
      return selectedStartCal.getTime();
    }
  }

  public Date getSelectedEndDate() {
    if (selectedEndCal.getTimeInMillis() == 0) {
      return null;
    } else {
      return selectedEndCal.getTime();
    }
  }

  /** Clears out the hours/minutes/seconds/millis of a Calendar. */
  private static void setMidnight(Calendar cal) {
    cal.set(HOUR_OF_DAY, 0);
    cal.set(MINUTE, 0);
    cal.set(SECOND, 0);
    cal.set(MILLISECOND, 0);
  }

  @Override public void handleClick(MonthCellDescriptor cell) {
    if (betweenDates(cell.getDate(), minCal, maxCal)) {

      // Three cases:
      //  1. First click selects start date
      //  2. Second completes the range
      //  3. Third click resets and sets start date again

      if (selectedStartCal.getTimeInMillis() == 0 ||
          selectedEndCal.getTimeInMillis() != 0) {

        // Case 1 or 3.  Begin new range.
        selectRange(cell.getDate(), null);
      } else {
        // Case 2.  Complete range, making sure that start comes before end.
        final Date startDate;
        final Date endDate;
        if (cell.getDate().before(selectedStartCal.getTime())) {
          startDate = cell.getDate();
          endDate = selectedStartCal.getTime();
        } else {
          startDate = selectedStartCal.getTime();
          endDate = cell.getDate();
        }
        selectRange(startDate, endDate);
      }
    }
  }

  private void selectCell(MonthCellDescriptor cell) {
    selectedCells.add(cell);
    cell.setSelected(true);
  }

  protected void selectRange(Date startDate, Date endDate) {
    validateRange(startDate, endDate);

    // De-select the currently-selected cells.
    for (MonthCellDescriptor cell : selectedCells) {
      cell.setSelected(false);
    }
    selectedCells.clear();

    // Find and select the new cells.
    selectCellsInRange(startDate, endDate);

    // Track the currently selected date range.
    if (startDate == null) {
      selectedStartCal.setTimeInMillis(0);
    } else {
      selectedStartCal.setTime(startDate);
    }

    if (endDate == null) {
      selectedEndCal.setTimeInMillis(0);
    } else {
      selectedEndCal.setTime(endDate);
    }

    // Update the adapter.
    adapter.notifyDataSetChanged();

    // Notify our listener
    if (listener != null) {
      if (endDate == null) {
        listener.onRangeStarted();
      } else {
        listener.onRangeCompleted();
      }
    }
  }

  private void selectCellsInRange(Date startDate, Date endDate) {
    boolean selecting = false;
    boolean inRange;

    for (List<List<MonthCellDescriptor>> month : cells) {
      for (List<MonthCellDescriptor> week : month) {
        for (MonthCellDescriptor cell : week) {

          // Skip cells that are for a different month
          if (cell.isCurrentMonth()) {
            inRange = betweenDates(cell.getDate(), startDate, endDate, true, true);
            if (inRange) {
              selecting = true;
              selectCell(cell);
            } else {
              if (selecting == true) {
                // We hit the end of the range.  We are done.
                return;
              }
            }
          }

        }
      }
    }
  }

  private class MonthAdapter extends BaseAdapter {
    private final LayoutInflater inflater;

    private MonthAdapter() {
      inflater = LayoutInflater.from(getContext());
    }

    @Override public boolean isEnabled(int position) {
      // Disable selectability: each cell will handle that itself.
      return false;
    }

    @Override public int getCount() {
      return months.size();
    }

    @Override public Object getItem(int position) {
      return months.get(position);
    }

    @Override public long getItemId(int position) {
      return position;
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
      MonthView monthView = (MonthView) convertView;
      if (monthView == null) {
        monthView = MonthView.create(getMonthResourceId(), parent, inflater, weekdayNameFormat,
                                     CalendarRangePickerView.this, today);
      }
      monthView.init(months.get(position), cells.get(position));
      return monthView;
    }
  }

  /**
   * @param month Descriptor for month we are working with
   * @param startCal Calendar for the month we are working with
   * @param selectedStartDate Earliest initially selected date, inclusive.
   * @param selectedEndDate Latest initially selected date, inclusive.
   */
  List<List<MonthCellDescriptor>> getMonthCells(MonthDescriptor month, Calendar startCal,
      Calendar selectedStartDate, Calendar selectedEndDate) {

    Calendar cal = Calendar.getInstance();

    cal.setTime(startCal.getTime());
    List<List<MonthCellDescriptor>> cells = new ArrayList<List<MonthCellDescriptor>>();
    cal.set(DAY_OF_MONTH, 1);
    int firstDayOfWeek = cal.get(DAY_OF_WEEK);
    cal.add(DATE, SUNDAY - firstDayOfWeek);
    while ((cal.get(MONTH) < month.getMonth() + 1 || cal.get(YEAR) < month.getYear()) //
        && cal.get(YEAR) <= month.getYear()) {
      Logr.d("Building week row starting at %s", cal.getTime());
      List<MonthCellDescriptor> weekCells = new ArrayList<MonthCellDescriptor>();
      cells.add(weekCells);
      for (int c = 0; c < 7; c++) {
        Date date = cal.getTime();
        boolean isCurrentMonth = cal.get(MONTH) == month.getMonth();
        boolean isSelected = isCurrentMonth &&
          betweenDates(cal, selectedStartDate, selectedEndDate, true, true);

        boolean isSelectable = isCurrentMonth && betweenDates(cal, minCal, maxCal);
        boolean isToday = sameDate(cal, today);
        int value = cal.get(DAY_OF_MONTH);
        MonthCellDescriptor cell =
          createDescriptor(date, isCurrentMonth, isSelectable, isSelected, isToday, value);
        if (isSelected) {
          selectedCells.add(cell);
        }
        weekCells.add(cell);
        cal.add(DATE, 1);
      }
    }
    return cells;
  }

  protected MonthCellDescriptor createDescriptor(Date date, boolean currentMonth,
                                                 boolean selectable, boolean selected,
                                                 boolean today, int value) {

    return new MonthCellDescriptor(date, currentMonth, selectable, selected, today, value);
  }

  private static boolean sameDate(Calendar cal, Calendar selectedDate) {
    return cal.get(MONTH) == selectedDate.get(MONTH)
        && cal.get(YEAR) == selectedDate.get(YEAR)
        && cal.get(DAY_OF_MONTH) == selectedDate.get(DAY_OF_MONTH);
  }

  /**
   * Include minCal but exclude maxCal
   */
  private static boolean betweenDates(Calendar cal, Calendar minCal, Calendar maxCal) {
    return betweenDates(cal, minCal, maxCal, true, false);
  }

  private static boolean betweenDates(Calendar cal, Calendar minCal, Calendar maxCal,
                                      boolean includeMin, boolean includeMax) {
    return betweenDates(cal.getTime(), minCal, maxCal, includeMin, includeMax);
  }

  /**
   * Include minCal but exclude maxCal
   */
  private static boolean betweenDates(Date date, Calendar minCal, Calendar maxCal) {
    return betweenDates(date, minCal, maxCal, true, false);
  }

  private static boolean betweenDates(Date date, Calendar minCal, Calendar maxCal,
                                      boolean includeMin, boolean includeMax) {

    Date min = null;
    Date max = null;

    if (minCal != null && minCal.getTimeInMillis() != 0) {
      min = minCal.getTime();
    }
    if (maxCal != null && maxCal.getTimeInMillis() != 0) {
      max = maxCal.getTime();
    }

    return betweenDates(date, min, max, includeMin, includeMax);
  }

  // Include min but exclude max
  private static boolean betweenDates(Date date, Date min, Date max) {
    return betweenDates(date, min, max, true, false);
  }

  private static boolean betweenDates(Date date, Date min, Date max, boolean includeMin, boolean includeMax) {
    if (date != null && min != null) {
      // Case 1: No max date.  Only a match if we are exactly on the min date.
      if (max == null && includeMin && date.equals(min)) {
        return true;
      }

      // Case 2: Full range available
      if (max != null &&
          ( date.after(min)  || (includeMin && date.equals(min)) ) &&
          ( date.before(max) || (includeMax && date.equals(max)) )) {
        return true;
      }
    }

    return false;
  }

}
