package org.kobjects.nativehtml.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.kobjects.nativehtml.css.CssProperty;
import org.kobjects.nativehtml.css.CssStyleDeclaration;
import org.kobjects.nativehtml.dom.Element;
import org.kobjects.nativehtml.dom.HtmlCollection;
import org.kobjects.nativehtml.html.HtmlComponent;


public class TableLayout implements Layout {

  static int getColSpan(Element cell) {
    String colSpanStr = cell.getAttribute("colspan");
    if (colSpanStr != null && !colSpanStr.isEmpty()) {
      try {
        return Integer.parseInt(colSpanStr.trim());
      } catch (Exception e) {
      }
    }
    return 1;
  }

  static int getRowSpan(Element cell) {
    String colSpanStr = cell.getAttribute("rowspan");
    if (colSpanStr != null && !colSpanStr.isEmpty()) {
      try {
        return Integer.parseInt(colSpanStr.trim());
      } catch (Exception e) {
      }
    }
    return 1;
  }

  @Override
  public void layout(Element parent, int xOfs, int yOfs, int contentWidth, boolean measureOnly, int[] result) {
     
    HtmlCollection rowCollection = parent.getChildren();
		  
    // Phase one: Measure
    
    int columnCount = 0;
    ArrayList<ColumnData> columnDataList = new ArrayList<>();
    for (int rowIndex = 0; rowIndex < rowCollection.getLength(); rowIndex++) {
      Element row = rowCollection.item(rowIndex);
      int columnIndex = 0;
      HtmlCollection columnCollection = row.getChildren();
      for (int rawColumnIndex = 0; rawColumnIndex < columnCollection.getLength(); rawColumnIndex++) {
        ColumnData columnData;
        HtmlComponent cell = (HtmlComponent) columnCollection.item(rawColumnIndex);
        
        // Find the columnData matching the logical column index, taking row- and colSpans into account.
        while (true) {
          while (columnDataList.size() <= columnIndex) {
            columnDataList.add(new ColumnData());
          }
          columnData = columnDataList.get(columnIndex);
          if (columnData.remainingRowSpan == 0) {
            break;
          }
          columnData.remainingRowSpan--;
          columnIndex++;
        }

        int cellWidth = ElementLayoutHelper.getBorderBoxWidth(cell, contentWidth);
        int colSpan = getColSpan(cell);
        int rowSpan = getRowSpan(cell);
        
        if (colSpan == 1) {
          columnData.maxMeasuredWidth = Math.max(columnData.maxMeasuredWidth, cellWidth);
        } else {
          if (columnData.maxWidthForColspan == null) {
            columnData.maxWidthForColspan = new HashMap<>();
          }
          Integer old = columnData.maxWidthForColspan.get(colSpan);
          columnData.maxWidthForColspan.put(colSpan, old == null ? cellWidth : Math.max(old, cellWidth));
          while (columnDataList.size() < columnIndex + colSpan) {
            columnDataList.add(new ColumnData());
          }
        }
        if (rowSpan > 0) {
          for (int i = 0; i < colSpan; i++) {
            columnDataList.get(columnIndex + i).remainingRowSpan = rowSpan;
          }
        }
        columnIndex += colSpan;
      }
      columnCount = Math.max(columnCount, columnIndex);
    }
    while (columnDataList.size() <= columnCount) {
      columnDataList.add(new ColumnData());
    }

    int borderSpacing = parent.getComputedStyle().getPx(CssProperty.BORDER_SPACING, contentWidth);
    
    // Phase two: Resolve

    int availableWidth = contentWidth - columnCount * borderSpacing;
    int totalWidth = 0;
    
    for (int i = 0; i < columnDataList.size(); i++) {
      ColumnData columnData = columnDataList.get(i);
      columnData.remainingRowSpan = 0;
      if (columnData.maxWidthForColspan != null) {
        for (Map.Entry<Integer,Integer> e : columnData.maxWidthForColspan.entrySet()) {
          int span = e.getKey();
          int spanWidth = e.getValue();
          int curWidth = 0;
          for (int j = i; j < i + span; j++) {
            curWidth += columnDataList.get(j).maxMeasuredWidth;
          }
          if (curWidth < spanWidth) {
            int distribute = (spanWidth - curWidth) / span;
            for (int j = i; j < i + span; j++) {
              columnDataList.get(j).maxMeasuredWidth += distribute;
            }
          }
        }
      }
      
      totalWidth += columnData.maxMeasuredWidth;
    }

    System.out.println("Table desired total width: " + totalWidth + " max avail: " + availableWidth);

    if (totalWidth > availableWidth /* || widthMeasureSpec == View.MeasureSpec.EXACTLY */) {
      for (ColumnData columnData : columnDataList) {
        System.out.println("Reducing column width " + columnData.maxMeasuredWidth + " to " + (columnData.maxMeasuredWidth * availableWidth / totalWidth));
        columnData.maxMeasuredWidth = columnData.maxMeasuredWidth * availableWidth / totalWidth;
      }
      totalWidth = availableWidth;
    }

    // Phase three: Layout

    int currentY = 0;
    for (int rowIndex = 0; rowIndex < rowCollection.getLength(); rowIndex++) {
      HtmlComponent row = (HtmlComponent) rowCollection.item(rowIndex);
      int columnIndex = 0;
      int rowHeight = 0;
      
      HtmlCollection cellCollection = row.getChildren();
      for (int physicalCellIndex = 0; physicalCellIndex < cellCollection.getLength(); physicalCellIndex++) {
        HtmlComponent cell = (HtmlComponent) cellCollection.item(physicalCellIndex);
        ColumnData columnData;
        while (true) {
          // Skip columns with remaining rowspan
          while (columnDataList.size() <= columnIndex) {
            columnDataList.add(new ColumnData());
          }
          columnData = columnDataList.get(columnIndex);
          if (columnData.remainingRowSpan == 0) {
            break;
          }
          
          columnIndex++;
        }
        CssStyleDeclaration cellStyle = cell.getComputedStyle();
        int topOffset = cellStyle.getPx(CssProperty.BORDER_TOP_WIDTH, contentWidth) 
            + cellStyle.getPx(CssProperty.PADDING_TOP, contentWidth);
        int bottomOffset = cellStyle.getPx(CssProperty.BORDER_BOTTOM_WIDTH, contentWidth) 
            + cellStyle.getPx(CssProperty.PADDING_BOTTOM, contentWidth);
        int leftOffset = cellStyle.getPx(CssProperty.BORDER_LEFT_WIDTH, contentWidth) 
            + cellStyle.getPx(CssProperty.PADDING_LEFT, contentWidth);
        int rightOffset = cellStyle.getPx(CssProperty.BORDER_RIGHT_WIDTH, contentWidth)
            + cellStyle.getPx(CssProperty.PADDING_RIGHT, contentWidth);
        int colSpan = getColSpan(cell);
        int spanWidth = 0;
        for (int i = columnIndex; i < columnIndex + colSpan; i++) {
          spanWidth += columnDataList.get(i).maxMeasuredWidth;
          if (i > columnIndex) {
            spanWidth += borderSpacing;
          }
        }
        
//      cell.measure(View.MeasureSpec.EXACTLY | (spanWidth - leftOffset - rightOffset), View.MeasureSpec.UNSPECIFIED);
        
//        cellParams.setMeasuredPosition(currentX + leftOffset, currentY + topOffset);

        int cellContentBoxHeight = ElementLayoutHelper.getContentBoxHeight(cell, spanWidth - leftOffset - rightOffset, contentWidth);
        //columnData.
            
        columnData.remainingRowSpan = getRowSpan(cell);
        columnData.remainingHeight = topOffset + cellContentBoxHeight + bottomOffset;
        columnData.startCell = cell;

        columnData.spanWidth = spanWidth;
        columnData.yOffset = currentY;
        
        columnIndex += colSpan;
      }
      
      for (ColumnData columnData : columnDataList) {
        if (columnData.remainingRowSpan == 1) {
          rowHeight = Math.max(rowHeight, columnData.remainingHeight);
        }
      }
      
      if (!measureOnly) {
        row.setBorderBoxBounds(0, currentY, contentWidth, rowHeight, contentWidth);
      }
      
      int currentX = 0;
      for (ColumnData columnData : columnDataList) {
        if (columnData.remainingRowSpan == 1) {
          columnData.remainingRowSpan = 0;
          if (!measureOnly) {
            // columnData.startCell.measure(View.MeasureSpec.EXACTLY | columnData.startCell.getMeasuredWidth(),
            //    View.MeasureSpec.EXACTLY | ());
            columnData.startCell.setBorderBoxBounds(
                currentX, 
                0,
                columnData.spanWidth,
                currentY + rowHeight - columnData.yOffset, contentWidth);
          }
          
        } else if (columnData.remainingRowSpan > 1) {
          columnData.remainingHeight -= rowHeight - borderSpacing;
          columnData.remainingRowSpan--;
        }
        currentX += columnData.maxMeasuredWidth + borderSpacing;
      }
      currentY += rowHeight + borderSpacing;
    }
    
    if (result != null) {
      result[0] = totalWidth;
      result[1] = currentY - borderSpacing;
    }
  }




  static class ColumnData {
    int maxMeasuredWidth;
    Map<Integer,Integer> maxWidthForColspan;

    int spanWidth;
    int remainingRowSpan;
    int remainingHeight;
    HtmlComponent startCell;
    int yOffset;
  }




}