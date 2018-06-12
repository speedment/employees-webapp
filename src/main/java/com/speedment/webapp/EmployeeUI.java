package com.speedment.webapp;

import javax.servlet.annotation.WebServlet;

import com.company.employees.employees.employees.departments.Departments;
import com.company.employees.employees.employees.employees.Employees;
import com.speedment.common.benchmark.Stopwatch;
import com.vaadin.addon.charts.Chart;
import com.vaadin.addon.charts.ChartOptions;
import com.vaadin.addon.charts.model.*;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.*;
import com.vaadin.ui.Label;

import java.util.*;

import static com.company.employees.employees.employees.employees.generated.GeneratedEmployees.*;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

/**
 * This UI is the application entry point. A UI may either represent a browser window
 * (or tab) or some part of an HTML page where a Vaadin application is embedded.
 *
 * @Author julgus
 */
@Theme("mytheme")
public class EmployeeUI extends UI {

    /* Instance variables */
    private TextField noOfEmployees, averageSalary;
    private Chart genderChart, salaryChart;
    private ListSeries maleCount, femaleCount;
    private DataSeries maleSalaryData, femaleSalaryData, maleCount2, femaleCount2;
    private Configuration genderChartConfig, salaryChartConfig;

    /* Init method is called when application starts */
    @Override
    protected void init(VaadinRequest vaadinRequest) {

        /* ------- TEXT COMPONENTS ------- */

        /* Label to display application title */
        Label appTitle = new Label("Employee Application");
        appTitle.setStyleName("h2");

        /* Text field to hold the number of employees */
        noOfEmployees = new TextField("Number of employees");
        noOfEmployees.setReadOnly(true);
        noOfEmployees.setStyleName("huge borderless");

        /* Text field to hold the average salary for selected department */
        averageSalary = new TextField("Average Salary");
        averageSalary.setReadOnly(true);
        averageSalary.setStyleName("huge borderless");

        /* ------- CHARTS ------- */

        ChartOptions.get().setTheme(new ChartTheme());

        /* Column chart to view balance between female and male employees at a certain department */
        genderChart = new Chart(ChartType.COLUMN);
        genderChart.setHeight(100, Unit.PERCENTAGE);
        Configuration genderChartConfig = genderChart.getConfiguration();
        genderChartConfig.getLegend().setEnabled(false); // Hide legend since salaryChart uses same series
        genderChartConfig.setTitle("Gender Balance");

        maleCount = new ListSeries("Male", 0); // 0 is only used as an init value, chart is populated with data below
        femaleCount = new ListSeries("Female", 0);
        genderChartConfig.setSeries(maleCount, femaleCount);

        XAxis x1 = new XAxis();
        x1.setCategories("Gender");
        genderChartConfig.addxAxis(x1);

        YAxis y1 = new YAxis();
        y1.setMax(50000); // Fixed value to highlight changes between departments
        y1.setTitle("Number of employees");
        genderChartConfig.addyAxis(y1);

        /* Column chart to view how salaries are dispersed at a certain department */
        salaryChart = new Chart(ChartType.AREASPLINE);
        salaryChart.setHeight(100, Unit.PERCENTAGE);
        salaryChartConfig = salaryChart.getConfiguration();
        salaryChartConfig.setTitle("Salary Distribution");
        PlotOptionsAreaspline plotOption = new PlotOptionsAreaspline();
        plotOption.setAnimation(false);
        salaryChartConfig.setPlotOptions(plotOption);

        /* Legend settings */
        Legend legend = salaryChartConfig.getLegend();
        legend.setLayout(LayoutDirection.VERTICAL);
        legend.setAlign(HorizontalAlign.RIGHT);
        legend.setVerticalAlign(VerticalAlign.TOP);
        legend.setX(-50);
        legend.setY(50);
        legend.setFloating(true);

        maleSalaryData = new DataSeries("Male");
        femaleSalaryData = new DataSeries("Female");

        salaryChartConfig.setSeries(maleSalaryData, femaleSalaryData);

        XAxis x2 = new XAxis();
        x2.setTitle("Salary in $");
        salaryChartConfig.addxAxis(x2);

        YAxis y2 = new YAxis();
        y2.setTitle("Number of employees");
        salaryChartConfig.addyAxis(y2);

        /* ------ SELECTOR ------- */

        /* Default department to use when starting application */
        final Departments defaultDept = DataModel.departments().findFirst().orElseThrow(NoSuchElementException::new);

        /* Native Select component to enable selection of Department */
        NativeSelect<Departments> selectDepartment = new NativeSelect<>("Select department");
        selectDepartment.setStyleName("huge borderless");
        selectDepartment.setItems(DataModel.departments());
        selectDepartment.setItemCaptionGenerator(Departments::getDeptName);
        selectDepartment.setEmptySelectionAllowed(false);
        selectDepartment.setSelectedItem(defaultDept);
        selectDepartment.addSelectionListener(e ->
                updateUI(e.getSelectedItem().orElseThrow()) // Listens for new selections and updates TextFields and Charts
        );

        /* ------ FILL COMPONENTS WITH VALUES FOR DEFAULT DEPARTMENT ------ */

        updateUI(defaultDept);

        /* ------- LAYOUTS TO HOLD COMPONENTS ------- */
        /* Main content layout */
        HorizontalLayout contents = new HorizontalLayout();
        contents.setSizeFull();

        /* Menu layout */
        VerticalLayout menu = new VerticalLayout();
        menu.setWidth(350, Unit.PIXELS);

        /* Body layout */
        VerticalLayout body = new VerticalLayout();
        body.setSizeFull();

        /* ------ FINAL ASSEMBLY ----- */
        menu.addComponents(appTitle, selectDepartment, noOfEmployees, averageSalary);
        body.addComponents(genderChart, salaryChart);
        contents.addComponent(menu);
        contents.addComponentsAndExpand(body); // Fill the area to the right of the menu
        setContent(contents);
    }


    /* Method that updates graphs and labels in GUI when a new department is selected */
    private void updateUI(Departments dept) {

        final Stopwatch sw = Stopwatch.createStarted();
        final Map<Employees.Gender, Long> counts = DataModel.countEmployees(dept);
        sw.stop();
        System.out.format("Counting employees for each Gender took %s and was %s%n",  sw, counts);

        /* Update noOfEmployees Label */
        noOfEmployees.setValue(format("%,d", counts.values().stream().mapToLong(l -> l).sum()));

        /* Update averageSalary Label */
        averageSalary.setValue(format("$%,d", DataModel.averageSalary(dept).intValue()));

        /* Update Gender Chart */
        maleCount.updatePoint(0, counts.getOrDefault(Gender.M, 0L));
        femaleCount.updatePoint(0, counts.getOrDefault(Gender.F, 0L));

        final List<DataSeriesItem> maleSalaries = new ArrayList<>();
        final List<DataSeriesItem> femaleSalaries = new ArrayList<>();

        DataModel.freqAggregation(dept)
            .streamAndClose()
            .forEach(agg -> {
                (agg.getGender() == Gender.F ? femaleSalaries : maleSalaries)
                    .add(new DataSeriesItem(agg.getInterval() * 1_000, agg.getFrequency()));
            });

        Comparator<DataSeriesItem> comparator = Comparator.comparingDouble((DataSeriesItem dsi) -> dsi.getX().doubleValue());

        maleSalaries.sort(comparator);
        femaleSalaries.sort(comparator);

        maleSalaryData.setData(maleSalaries);
        femaleSalaryData.setData(femaleSalaries);
        //salaryChartConfig.setSeries(maleSalaryData, femaleSalaryData);
        salaryChart.drawChart();
    }

    @WebServlet(urlPatterns = "/*", name = "MyUIServlet", asyncSupported = true)
    @VaadinServletConfiguration(ui = EmployeeUI.class, productionMode = false)
    public static class MyUIServlet extends VaadinServlet {
    }


    /*    private List<DataSeriesItem> getSalaryDataSeries(Map<Gender, Map<Integer, Long>> dataMap, Gender gender) {
        Map<Integer, Long> intervalFrequencyMap = dataMap.getOrDefault(gender, Collections.emptyMap());
        return IntStream.rangeClosed(35, 130) // Values selected based on salaries in database
                .mapToObj(x -> new DataSeriesItem(x * 1000, intervalFrequencyMap.getOrDefault(x, 0L)))
                .collect(Collectors.toList());
    }*/

}
