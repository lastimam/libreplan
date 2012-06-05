/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2012 Igalia, S.L.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.libreplan.web.users.dashboard;

import static org.libreplan.web.I18nHelper._;
import static org.libreplan.web.planner.tabs.MultipleTabsPlannerController.BREADCRUMBS_SEPARATOR;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.joda.time.LocalDate;
import org.libreplan.business.orders.entities.OrderElement;
import org.libreplan.business.workingday.EffortDuration;
import org.libreplan.web.common.Util;
import org.libreplan.web.common.components.bandboxsearch.BandboxSearch;
import org.libreplan.web.common.entrypoints.EntryPointsHandler;
import org.libreplan.web.common.entrypoints.EntryPointsHandler.ICapture;
import org.libreplan.web.common.entrypoints.IURLHandlerRegistry;
import org.libreplan.web.users.services.CustomTargetUrlResolver;
import org.springframework.util.Assert;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.WrongValueException;
import org.zkoss.zk.ui.util.GenericForwardComposer;
import org.zkoss.zul.Button;
import org.zkoss.zul.Cell;
import org.zkoss.zul.Column;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Image;
import org.zkoss.zul.Label;
import org.zkoss.zul.Row;
import org.zkoss.zul.RowRenderer;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.api.Grid;

/**
 * Controller for creation/edition of a monthly timesheet
 *
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 */
@SuppressWarnings("serial")
public class MonthlyTimesheetController extends GenericForwardComposer
        implements IMonthlyTimesheetController {

    private final static String EFFORT_DURATION_TEXTBOX_WIDTH = "30px";

    private final static String WORK_REPORTS_URL = "/workreports/workReport.zul";

    private IMonthlyTimesheetModel monthlyTimesheetModel;

    private IURLHandlerRegistry URLHandlerRegistry;

    private Grid timesheet;

    private Columns columns;

    private BandboxSearch orderElementBandboxSearch;

    private Button previousMonth;

    private Button nextMonth;

    @Resource
    private IMonthlyTimesheetController monthlyTimesheetController;

    private RowRenderer rowRenderer = new RowRenderer() {

        private LocalDate first;
        private LocalDate last;

        @Override
        public void render(Row row, Object data) throws Exception {
            MonthlyTimesheetRow monthlyTimesheetRow = (MonthlyTimesheetRow) data;

            initMonthlyTimesheetDates();

            switch (monthlyTimesheetRow.getType()) {
            case ORDER_ELEMENT:
                renderOrderElementRow(row,
                        monthlyTimesheetRow.getOrderElemement());
                break;
            case CAPACITY:
                renderCapacityRow(row);
                break;
            case TOTAL:
                renderTotalRow(row);
                break;
            case EXTRA:
                renderExtraRow(row);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown MonthlyTimesheetRow type: "
                                + monthlyTimesheetRow.getType());
            }
        }

        private void initMonthlyTimesheetDates() {
            first = monthlyTimesheetModel.getFirstDay();
            last = monthlyTimesheetModel.getLastDate();
        }

        private void renderOrderElementRow(Row row, OrderElement orderElement) {
            Util.appendLabel(row, monthlyTimesheetModel.getOrder(orderElement)
                    .getName());
            Util.appendLabel(row, orderElement.getName());

            appendInputsForDays(row, orderElement);

            appendTotalColumn(row, orderElement);
        }

        private void appendInputsForDays(Row row,
                final OrderElement orderElement) {
            for (LocalDate day = first; day.compareTo(last) <= 0; day = day
                    .plusDays(1)) {
                final LocalDate textboxDate = day;

                final Textbox textbox = new Textbox();
                textbox.setWidth(EFFORT_DURATION_TEXTBOX_WIDTH);

                Util.bind(textbox, new Util.Getter<String>() {
                    @Override
                    public String get() {
                        EffortDuration effortDuration = monthlyTimesheetModel
                                .getEffortDuration(orderElement, textboxDate);
                        return effortDurationToString(effortDuration);
                    }
                }, new Util.Setter<String>() {
                    @Override
                    public void set(String value) {
                        EffortDuration effortDuration = effortDurationFromString(value);
                        if (effortDuration == null) {
                            throw new WrongValueException(textbox,
                                    _("Not a valid effort duration"));
                        }
                        monthlyTimesheetModel.setEffortDuration(orderElement,
                                textboxDate, effortDuration);
                        markAsModified(textbox);
                        updateTotals(orderElement, textboxDate);
                    }

                    private void updateTotals(OrderElement orderElement,
                            LocalDate date) {
                        updateTotalColumn(orderElement);
                        updateTotalRow(date);
                        updateExtraRow(date);
                        updateTotalColumn();
                        updateTotalExtraColumn();
                    }

                });

                if (monthlyTimesheetModel
                        .wasModified(orderElement, textboxDate)) {
                    markAsModified(textbox);
                }

                Cell cell = getCenteredCell(textbox);
                if (monthlyTimesheetModel.getResourceCapacity(day).isZero()) {
                    setBackgroundNonCapacityCell(cell);
                }
                row.appendChild(cell);
            }

        }

        private void markAsModified(final Textbox textbox) {
            textbox.setStyle("border-color: red;");
        }

        private void appendTotalColumn(Row row, final OrderElement orderElement) {
            row.appendChild(getDisabledTextbox(getTotalRowTextboxId(orderElement)));
            updateTotalColumn(orderElement);
        }

        private void updateTotalColumn(OrderElement orderElement) {
            Textbox textbox = (Textbox) timesheet.getFellow(getTotalRowTextboxId(orderElement));
            textbox.setValue(effortDurationToString(monthlyTimesheetModel
                    .getEffortDuration(orderElement)));
        }

        private void renderTotalRow(Row row) {
            appendLabelSpaningTwoColumns(row, _("Total"));
            appendTotalForDays(row);
            appendTotalColumn(row);
        }

        private void appendLabelSpaningTwoColumns(Row row, String text) {
            Cell cell = new Cell();
            cell.setColspan(2);
            Label label = new Label(text);
            label.setStyle("font-weight: bold;");
            cell.appendChild(label);
            row.appendChild(cell);
        }

        private void appendTotalForDays(Row row) {
            for (LocalDate day = first; day.compareTo(last) <= 0; day = day
                    .plusDays(1)) {
                Cell cell = getCenteredCell(getDisabledTextbox(getTotalColumnTextboxId(day)));
                if (monthlyTimesheetModel.getResourceCapacity(day).isZero()) {
                    setBackgroundNonCapacityCell(cell);
                }
                row.appendChild(cell);

                updateTotalRow(day);
            }
        }

        private void updateTotalRow(LocalDate date) {
            Textbox textbox = (Textbox) timesheet
                    .getFellow(getTotalColumnTextboxId(date));
            textbox.setValue(effortDurationToString(monthlyTimesheetModel
                    .getEffortDuration(date)));
        }

        private void appendTotalColumn(Row row) {
            row.appendChild(getAlignLeftCell(getDisabledTextbox(getTotalTextboxId())));
            updateTotalColumn();
        }

        private void updateTotalColumn() {
            Textbox textbox = (Textbox) timesheet
                    .getFellow(getTotalTextboxId());
            textbox.setValue(effortDurationToString(monthlyTimesheetModel
                    .getTotalEffortDuration()));
        }

        private void renderCapacityRow(Row row) {
            appendLabelSpaningTwoColumns(row, _("Capacity"));
            appendCapcityForDaysAndTotal(row);
        }

        private void appendCapcityForDaysAndTotal(Row row) {
            EffortDuration totalCapacity = EffortDuration.zero();

            for (LocalDate day = first; day.compareTo(last) <= 0; day = day
                    .plusDays(1)) {
                EffortDuration capacity = monthlyTimesheetModel
                        .getResourceCapacity(day);

                Cell cell = getCenteredCell(getDisabledTextbox(
                        getCapcityColumnTextboxId(day), capacity));
                if (monthlyTimesheetModel.getResourceCapacity(day).isZero()) {
                    setBackgroundNonCapacityCell(cell);
                }
                row.appendChild(cell);

                totalCapacity = totalCapacity.plus(capacity);
            }

            row.appendChild(getAlignLeftCell(getDisabledTextbox(
                    getTotalCapacityTextboxId(), totalCapacity)));
        }

        private void renderExtraRow(Row row) {
            appendLabelSpaningTwoColumns(row, _("Extra"));
            appendExtraForDays(row);
            appendTotalExtra(row);
        }

        private void appendExtraForDays(Row row) {
            for (LocalDate day = first; day.compareTo(last) <= 0; day = day
                    .plusDays(1)) {
                Cell cell = getCenteredCell(getDisabledTextbox(getExtraColumnTextboxId(day)));
                if (monthlyTimesheetModel.getResourceCapacity(day).isZero()) {
                    setBackgroundNonCapacityCell(cell);
                }
                row.appendChild(cell);

                updateExtraRow(day);
            }

        }

        private void updateExtraRow(LocalDate date) {
            EffortDuration total = getEffortDuration(getTotalColumnTextboxId(date));
            EffortDuration capacity = getEffortDuration(getCapcityColumnTextboxId(date));

            EffortDuration extra = EffortDuration.zero();
            if (total.compareTo(capacity) > 0) {
                extra = total.minus(capacity);
            }

            Textbox textbox = (Textbox) timesheet
                    .getFellow(getExtraColumnTextboxId(date));
            textbox.setValue(effortDurationToString(extra));
        }

        private EffortDuration getEffortDuration(String textboxId) {
            String value = ((Textbox) timesheet.getFellow(textboxId))
                    .getValue();
            return effortDurationFromString(value);
        }

        private void appendTotalExtra(Row row) {
            row.appendChild(getAlignLeftCell(getDisabledTextbox(getTotalExtraTextboxId())));
            updateTotalExtraColumn();
        }

        private void updateTotalExtraColumn() {
            EffortDuration totalExtra = EffortDuration.zero();
            for (LocalDate day = first; day.compareTo(last) <= 0; day = day
                    .plusDays(1)) {
                EffortDuration extra = getEffortDuration(getExtraColumnTextboxId(day));
                totalExtra = totalExtra.plus(extra);
            }

            Textbox textbox = (Textbox) timesheet
                    .getFellow(getTotalExtraTextboxId());
            textbox.setValue(effortDurationToString(totalExtra));
        }

        private String getTotalRowTextboxId(final OrderElement orderElement) {
            return "textbox-total-row" + orderElement.getId();
        }

        private String getTotalColumnTextboxId(LocalDate date) {
            return "textbox-total-column-" + date;
        }

        private String getTotalTextboxId() {
            return "textbox-total";
        }

        private String getCapcityColumnTextboxId(LocalDate date) {
            return "textbox-capacity-column-" + date;
        }

        private String getTotalCapacityTextboxId() {
            return "textbox-total-capacity";
        }

        private String getExtraColumnTextboxId(LocalDate date) {
            return "textbox-extra-column-" + date;
        }

        private String getTotalExtraTextboxId() {
            return "textbox-total-extra";
        }

        private Textbox getDisabledTextbox(String id) {
            Textbox textbox = new Textbox();
            textbox.setWidth(EFFORT_DURATION_TEXTBOX_WIDTH);
            textbox.setId(id);
            textbox.setDisabled(true);
            return textbox;
        }

        private Textbox getDisabledTextbox(String id, EffortDuration effort) {
            Textbox textbox = getDisabledTextbox(id);
            textbox.setValue(effortDurationToString(effort));
            return textbox;
        }

        private Cell getCenteredCell(Component component) {
            Cell cell = new Cell();
            cell.setAlign("center");
            cell.appendChild(component);
            return cell;
        }

        private Cell getAlignLeftCell(Component component) {
            Cell cell = new Cell();
            cell.setAlign("left");
            cell.appendChild(component);
            return cell;
        }

        private void setBackgroundNonCapacityCell(Cell cell) {
            cell.setStyle("background-color: #FFEEEE");
        }

        private String effortDurationToString(EffortDuration effort) {
            if (effort == null || effort.isZero()) {
                return "";
            }

            return effort.toFormattedString();
        }

        private EffortDuration effortDurationFromString(String effort) {
            if (StringUtils.isBlank(effort)) {
                return EffortDuration.zero();
            }

            return EffortDuration.parseFromFormattedString(effort);
        }

    };

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        comp.setAttribute("controller", this);

        setBreadcrumbs(comp);

        URLHandlerRegistry.getRedirectorFor(IMonthlyTimesheetController.class)
                .register(this, page);
    }

    private void setBreadcrumbs(Component comp) {
        Component breadcrumbs = comp.getPage().getFellow("breadcrumbs");
        if (breadcrumbs.getChildren() != null) {
            breadcrumbs.getChildren().clear();
        }
        breadcrumbs.appendChild(new Image(BREADCRUMBS_SEPARATOR));
        breadcrumbs.appendChild(new Label(_("My account")));
        breadcrumbs.appendChild(new Image(BREADCRUMBS_SEPARATOR));
        breadcrumbs.appendChild(new Label(_("My dashboard")));
        breadcrumbs.appendChild(new Image(BREADCRUMBS_SEPARATOR));
        breadcrumbs.appendChild(new Label(_("Monthly timesheet")));
    }

    @Override
    public void goToCreateOrEditForm(LocalDate date) {
        monthlyTimesheetModel.initCreateOrEdit(date);
        initTimesheet(date);
    }

    @Override
    public void goToCreateOrEditFormForResource(LocalDate date,
            org.libreplan.business.resources.entities.Resource resource) {
        monthlyTimesheetModel.initCreateOrEdit(date, resource);
        initTimesheet(date);
    }

    private void initTimesheet(LocalDate date) {
        columns = new Columns();
        timesheet.getChildren().clear();
        timesheet.appendChild(columns);
        createColumns(date);
    }

    private void createColumns(LocalDate date) {
        createProjectAndTaskColumns();
        createColumnsForDays(date);
        createTotalColumn();
    }

    private void createProjectAndTaskColumns() {
        columns.appendChild(new Column(_("Project")));
        columns.appendChild(new Column(_("Task")));
    }

    private void createColumnsForDays(LocalDate date) {
        LocalDate start = date.dayOfMonth().withMinimumValue();
        LocalDate end = date.dayOfMonth().withMaximumValue();

        for (LocalDate day = start; day.compareTo(end) <= 0; day = day
                .plusDays(1)) {
            Column column = new Column(day.getDayOfMonth() + "");
            column.setAlign("center");
            columns.appendChild(column);
        }
    }

    private void createTotalColumn() {
        columns.appendChild(new Column(_("Total")));
    }

    public String getDate() {
        return monthlyTimesheetModel.getDate().toString("MMMM y");
    }

    public String getResource() {
        return monthlyTimesheetModel.getWorker().getShortDescription();
    }

    public List<MonthlyTimesheetRow> getRows() {
        List<MonthlyTimesheetRow> result = MonthlyTimesheetRow
                .wrap(monthlyTimesheetModel
                .getOrderElements());
        result.add(MonthlyTimesheetRow.createCapacityRow());
        result.add(MonthlyTimesheetRow.createTotalRow());
        result.add(MonthlyTimesheetRow.createExtraRow());
        return result;
    }

    public RowRenderer getRowRenderer() {
        return rowRenderer;
    }

    public void save() {
        monthlyTimesheetModel.save();
        String url = CustomTargetUrlResolver.USER_DASHBOARD_URL
                + "?timesheet_save=" + monthlyTimesheetModel.getDate();
        if (!monthlyTimesheetModel.isCurrentUser()) {
            url = WORK_REPORTS_URL + "?timesheet_save=true";
        }
        Executions.getCurrent().sendRedirect(url);
    }

    public void saveAndContinue() {
        monthlyTimesheetModel.save();
        if (monthlyTimesheetModel.isCurrentUser()) {
            goToCreateOrEditForm(monthlyTimesheetModel.getDate());
        } else {
            goToCreateOrEditFormForResource(monthlyTimesheetModel.getDate(),
                    monthlyTimesheetModel.getWorker());
        }
        Util.reloadBindings(timesheet);
    }

    public void cancel() {
        monthlyTimesheetModel.cancel();
        String url = CustomTargetUrlResolver.USER_DASHBOARD_URL;
        if (!monthlyTimesheetModel.isCurrentUser()) {
            url = WORK_REPORTS_URL;
        }
        Executions.getCurrent().sendRedirect(url);
    }

    public void addOrderElement() {
        OrderElement orderElement = (OrderElement) orderElementBandboxSearch
                .getSelectedElement();
        if (orderElement != null) {
            monthlyTimesheetModel.addOrderElement(orderElement);
            orderElementBandboxSearch.setSelectedElement(null);
            Util.reloadBindings(timesheet);
        }
    }

    public boolean isFirstMonth() {
        return monthlyTimesheetModel.isFirstMonth();
    }

    public boolean isLastMonth() {
        return monthlyTimesheetModel.isLastMonth();
    }

    public void previousMonth() {
        if (monthlyTimesheetModel.isModified()) {
            throw new WrongValueException(
                    previousMonth,
                    _("There are unsaved changes in the current monthly timesheet, please save before moving"));
        }
        sendToMonthlyTimesheet(monthlyTimesheetModel.getDate().minusMonths(1));
    }

    public void nextMonth() {
        if (monthlyTimesheetModel.isModified()) {
            throw new WrongValueException(
                    nextMonth,
                    _("There are unsaved changes in the current monthly timesheet, please save before moving"));
        }

        sendToMonthlyTimesheet(monthlyTimesheetModel.getDate().plusMonths(1));
    }

    private void sendToMonthlyTimesheet(final LocalDate date) {
        String capturePath = EntryPointsHandler.capturePath(new ICapture() {
            @Override
            public void capture() {
                monthlyTimesheetController.goToCreateOrEditForm(date);
            }
        });
        Executions.getCurrent().sendRedirect(capturePath);
    }

    public boolean isCurrentUser() {
        return monthlyTimesheetModel.isCurrentUser();
    }

    public boolean isNotCurrentUser() {
        return !monthlyTimesheetModel.isCurrentUser();
    }

}

/**
 * Simple class to represent the the rows in the monthly timesheet grid.<br />
 *
 * This is used to mark the special rows like capacity and total.
 */
class MonthlyTimesheetRow {
    enum MonthlyTimesheetRowType {
        ORDER_ELEMENT, CAPACITY, TOTAL, EXTRA
    };

    private MonthlyTimesheetRowType type;
    private OrderElement orderElemement;

    public static MonthlyTimesheetRow createOrderElementRow(
            OrderElement orderElemement) {
        MonthlyTimesheetRow row = new MonthlyTimesheetRow(
                MonthlyTimesheetRowType.ORDER_ELEMENT);
        Assert.notNull(orderElemement);
        row.orderElemement = orderElemement;
        return row;
    }

    public static MonthlyTimesheetRow createCapacityRow() {
        return new MonthlyTimesheetRow(MonthlyTimesheetRowType.CAPACITY);
    }

    public static MonthlyTimesheetRow createTotalRow() {
        return new MonthlyTimesheetRow(MonthlyTimesheetRowType.TOTAL);
    }

    public static MonthlyTimesheetRow createExtraRow() {
        return new MonthlyTimesheetRow(MonthlyTimesheetRowType.EXTRA);
    }

    public static List<MonthlyTimesheetRow> wrap(
            List<OrderElement> orderElements) {
        List<MonthlyTimesheetRow> result = new ArrayList<MonthlyTimesheetRow>();
        for (OrderElement each : orderElements) {
            result.add(createOrderElementRow(each));
        }
        return result;
    }

    private MonthlyTimesheetRow(MonthlyTimesheetRowType type) {
        this.type = type;
    }

    public MonthlyTimesheetRowType getType() {
        return type;
    }

    public OrderElement getOrderElemement() {
        return orderElemement;
    }

}
