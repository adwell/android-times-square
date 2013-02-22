// Copyright 2012 Square, Inc.
package com.squareup.timessquare;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;

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

public class MonthView extends LinearLayout {
  private TextView title;
  private CalendarGridView grid;
  private Listener listener;

  public static MonthView create(ViewGroup parent, LayoutInflater inflater,
      DateFormat weekdayNameFormat, Listener listener, Calendar today) {

    return create(R.layout.month, parent, inflater, weekdayNameFormat, listener, today);
  }

  public static MonthView create(int layoutId, ViewGroup parent, LayoutInflater inflater,
      DateFormat weekdayNameFormat, Listener listener, Calendar today) {

    final MonthView view = (MonthView) inflater.inflate(layoutId, parent, false);

    final int originalDayOfWeek = today.get(Calendar.DAY_OF_WEEK);

    final CalendarRowView headerRow = (CalendarRowView) view.grid.getChildAt(0);
    for (int c = Calendar.SUNDAY; c <= Calendar.SATURDAY; c++) {
      today.set(Calendar.DAY_OF_WEEK, c);
      final TextView textView = (TextView) headerRow.getChildAt(c - 1);
      textView.setText(weekdayNameFormat.format(today.getTime()));
    }
    today.set(Calendar.DAY_OF_WEEK, originalDayOfWeek);
    view.listener = listener;
    return view;
  }

  public MonthView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override protected void onFinishInflate() {
    super.onFinishInflate();
    title = (TextView) findViewById(R.id.title);
    grid = (CalendarGridView) findViewById(R.id.calendar_grid);

    if (title == null) {
      throw new RuntimeException(
        "Your content must have a TextView whose id attribute is 'R.id.title'");
    }

    if (grid == null) {
      throw new RuntimeException(
        "Your content must have a CalendarGridView whose id attribute is 'R.id.grid'");
    }
  }

  /**
   * Simple form of init for drawing a static calendar with nothing selectable.
   */
  public void init(DateFormat monthNameFormat, Calendar startCal) {
    final MonthDescriptor month = new MonthDescriptor(startCal.get(MONTH), startCal.get(YEAR),
        monthNameFormat.format(startCal.getTime()));

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
        boolean isSelected = false;
        boolean isSelectable = false;
        boolean isToday = false;
        int value = cal.get(DAY_OF_MONTH);
        MonthCellDescriptor cell =
            new MonthCellDescriptor(date, isCurrentMonth, isSelectable, isSelected, isToday, value);
        weekCells.add(cell);
        cal.add(DATE, 1);
      }
    }

    init(month, cells);
  }

  public void init(MonthDescriptor month, List<List<MonthCellDescriptor>> cells) {
    Logr.d("Initializing MonthView for %s", month);
    long start = System.currentTimeMillis();
    title.setText(month.getLabel());

    final int numRows = cells.size();
    for (int i = 0; i < 6; i++) {
      CalendarRowView weekRow = (CalendarRowView) grid.getChildAt(i + 1);
      weekRow.setListener(listener);
      if (i < numRows) {
        weekRow.setVisibility(VISIBLE);
        List<MonthCellDescriptor> week = cells.get(i);
        for (int c = 0; c < week.size(); c++) {
          MonthCellDescriptor cell = week.get(c);
          CheckedTextView cellView = (CheckedTextView) weekRow.getChildAt(c);
          cellView.setText(Integer.toString(cell.getValue()));
          stylizeCellView(cellView, cell);
          cellView.setTag(cell);
        }
      } else {
        weekRow.setVisibility(GONE);
      }
    }
    Logr.d("MonthView.init took %d ms", System.currentTimeMillis() - start);
  }

  protected void stylizeCellView(CheckedTextView cellView, MonthCellDescriptor cell) {
    cellView.setEnabled(cell.isCurrentMonth());
    cellView.setChecked(!cell.isToday());
    cellView.setSelected(cell.isSelected());
    if (cell.isSelectable()) {
      cellView.setTextColor(getResources().getColorStateList(R.color.calendar_text_selector));
    } else {
      cellView.setTextColor(getResources().getColor(R.color.calendar_text_unselectable));
    }
  }

  public interface Listener {
    void handleClick(MonthCellDescriptor cell);
  }
}
