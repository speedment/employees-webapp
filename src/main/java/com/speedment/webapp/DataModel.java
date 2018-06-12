package com.speedment.webapp;

import com.company.employees.EmployeesApplicationBuilder;
import com.company.employees.employees.employees.departments.Departments;
import com.company.employees.employees.employees.departments.DepartmentsManager;
import com.company.employees.employees.employees.dept_emp.DeptEmp;
import com.company.employees.employees.employees.dept_emp.DeptEmpManager;
import com.company.employees.employees.employees.employees.Employees;
import com.company.employees.employees.employees.salaries.Salaries;
import com.speedment.common.tuple.getter.TupleGetter0;
import com.speedment.common.tuple.getter.TupleGetter1;
import com.speedment.common.tuple.getter.TupleGetter2;
import com.speedment.enterprise.aggregator.Aggregation;
import com.speedment.enterprise.aggregator.Aggregator;
import com.speedment.enterprise.application.InMemoryBundle;
import com.speedment.enterprise.datastore.runtime.DataStoreComponent;
import com.speedment.runtime.core.ApplicationBuilder;
import com.speedment.runtime.core.Speedment;
import com.speedment.runtime.join.Join;
import com.speedment.runtime.join.JoinComponent;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

public final class DataModel {

    private static Speedment speedment;
    /* Date to allow filtering out current salaries */
    private static Date currentDate;

    /* Adjusts the size of the salary buckets, 1000 gives salary x-values of $ X k. */
    private static final int SALARY_BUCKET_SIZE = 1000;

    private DataModel() {
    }

    public static synchronized Speedment speedment() {
        if (speedment == null) {
            speedment = new EmployeesApplicationBuilder()
                .withUsername("") // User need to match database
                .withPassword("") // Password need to match database
                .withLogging(ApplicationBuilder.LogType.STREAM)
                .withLogging(ApplicationBuilder.LogType.APPLICATION_BUILDER)
                .withBundle(InMemoryBundle.class)
/*                .withParam("db.mysql.collationName", "utf8mb4_general_ci")
                .withParam("db.mysql.binaryCollationName", "utf8mb4_bin")*/
                .build();

            // Load a snapshot of the database into off-heap JVM-memoory
            speedment.get(DataStoreComponent.class).ifPresent(DataStoreComponent::load);

            currentDate = java.sql.Date.valueOf(LocalDate.now()); // The date allows filtering out current salaries
        }
        return speedment;
    }

    public static Stream<Departments> departments() {
        DepartmentsManager departments = speedment().getOrThrow(DepartmentsManager.class);
        return departments.stream();
    }

    public static Map<Employees.Gender, Long> countEmployees(Departments dept) {

        Join<DeptEmplEmployeesSalaries> join = joinDeptEmpSal(dept);

        Aggregator<DeptEmplEmployeesSalaries, ?, GenderCount> aggregator = Aggregator.builder(GenderCount::new)

            .firstOn(DeptEmplEmployeesSalaries.employeesGetter())
                .andThen(Employees.GENDER).key(GenderCount::setGender)

            .count(GenderCount::setCount)

            .build();

        try (Aggregation<GenderCount> aggregation = join.stream()
            .parallel()
            .collect(aggregator.createCollector())) {

            return aggregation.stream()
                .collect(
                    groupingBy(GenderCount::getGender,
                        reducing(
                            0L,
                            GenderCount::getCount,
                            (a, b) -> a + b
                        )
                    )
                );
        }
    }


    public static Double averageSalary(Departments dept) {
        Join<DeptEmplEmployeesSalaries> join = joinDeptEmpSal(dept);

        Aggregator<DeptEmplEmployeesSalaries, ?, AvgSalary> aggregator = Aggregator.builder(AvgSalary::new)
            .firstOn(DeptEmplEmployeesSalaries.salariesGetter())
               .andThen(Salaries.SALARY).average(AvgSalary::setAvgSalary)
            .build();

        try (Aggregation<AvgSalary> aggregation = join.stream()
            .parallel()
            .collect(aggregator.createCollector())) {

            return aggregation.stream()
                .mapToDouble(AvgSalary::getAvgSalary)
                .findFirst().orElse(0);
        }
    }



    public static Aggregation<GenderIntervalFrequency> freqAggregation(Departments dept) {

        Aggregator<DeptEmplEmployeesSalaries, ?, GenderIntervalFrequency> aggregator =

            // Provide a constructor for the "result object"
            Aggregator.builder(GenderIntervalFrequency::new)

                // Create a key on Gender
                .firstOn(DeptEmplEmployeesSalaries.employeesGetter())
                .andThen(Employees.GENDER)
                .key(GenderIntervalFrequency::setGender)

                // Create a key on salary divided by 1,000 as an integer
                .firstOn(DeptEmplEmployeesSalaries.salariesGetter())
                .andThen(Salaries.SALARY.divide(SALARY_BUCKET_SIZE).asInt())
                .key(GenderIntervalFrequency::setInterval)

                // For each unique set of keys, count the number of entitites
                .count(GenderIntervalFrequency::setFrequency)
                .build();


        return joinDeptEmpSal(dept)
            .stream()
            .parallel()
            .collect(aggregator.createCollector());

    }


    private static Join<DeptEmplEmployeesSalaries> joinDeptEmpSal(Departments dept) {
        JoinComponent jc = speedment().getOrThrow(JoinComponent.class);

        return jc.from(DeptEmpManager.IDENTIFIER)
                    // Only include data from the selected department
                    .where(DeptEmp.DEPT_NO.equal(dept.getDeptNo()))

                // Join in Employees with Employees.EMP_NO equal DeptEmp.EMP_NO
                .innerJoinOn(Employees.EMP_NO).equal(DeptEmp.EMP_NO)

                // Join Salaries with Salaries.EMP_NO) equal Employees.EMP_NO
                .innerJoinOn(Salaries.EMP_NO).equal(Employees.EMP_NO)
                      // Filter out historic salary data
                     .where(Salaries.TO_DATE.greaterOrEqual(currentDate))

                .build(DeptEmplEmployeesSalaries::new);
    }

    /* Class to represent the results of the average salary aggregation */
    private final static class AvgSalary {

        private double avgSalary;

        private void setAvgSalary(double avgSalary) {
            this.avgSalary = avgSalary;
        }

        private double getAvgSalary() {
            return avgSalary;
        }
    }

    private final static class GenderCount {

        private Employees.Gender gender;
        private long count;

        private void setGender(Employees.Gender gender) {
            this.gender = requireNonNull(gender);
        }

        private Employees.Gender getGender() {
            return gender;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }

    /* Class to represent the results of the salary distribution aggregation */
    public final static class GenderIntervalFrequency {

        private Employees.Gender gender;
        private int interval;
        private long frequency;

        public void setGender(Employees.Gender gender) {
            this.gender = gender;
        }

        public void setInterval(int interval) {
            this.interval = interval;
        }

        public void setFrequency(long frequency) {
            this.frequency = frequency;
        }

        public Employees.Gender getGender() {
            return gender;
        }

        public int getInterval() {
            return interval;
        }

        public long getFrequency() {
            return frequency;
        }
    }


    /* Class to represent the results of the salary distribution aggregation */
    public final static class DeptEmplEmployeesSalaries {

        private final DeptEmp deptEmp;
        private final Employees employees;
        private final Salaries salaries;

        public DeptEmplEmployeesSalaries(DeptEmp deptEmp, Employees employees, Salaries salaries) {
            this.deptEmp = requireNonNull(deptEmp);
            this.employees = requireNonNull(employees);
            this.salaries = requireNonNull(salaries);
        }

        public DeptEmp deptEmp() {
            return deptEmp;
        }

        public Employees employees() {
            return employees;
        }

        public Salaries salaries() {
            return salaries;
        }

        public static TupleGetter0<DeptEmplEmployeesSalaries, DeptEmp> deptEmpGetter() {
            return DeptEmplEmployeesSalaries::deptEmp;
        }

        public static TupleGetter1<DeptEmplEmployeesSalaries, Employees> employeesGetter() {
            return DeptEmplEmployeesSalaries::employees;
        }

        public static TupleGetter2<DeptEmplEmployeesSalaries, Salaries> salariesGetter() {
            return DeptEmplEmployeesSalaries::salaries;
        }

    }



/*    public static Map<GeneratedEmployees.Gender, Map<Integer, Long>> genderIntervalFrequencyDataJava(Departments dept) {
        return joinDeptEmpSal(dept).stream()
            .collect(
                Collectors.groupingBy(
                    t -> t.employees().getGender(),
                    groupingBy(t -> t.salaries().getSalary() / SALARY_BUCKET_SIZE,
                        counting()
                    )
                )
            );

    }

    public static Map<GeneratedEmployees.Gender, Map<Integer, Long>> genderIntervalFrequencyData(Departments dept) {

        Aggregator<DeptEmplEmployeesSalaries, ?, GenderIntervalFrequency> aggregator =

            // Provide a constructor for the "result object"
            Aggregator.builder(GenderIntervalFrequency::new)

                // Create a key on Gender
                .firstOn(DeptEmplEmployeesSalaries.employeesGetter())
                .andThen(Employees.GENDER)
                .key(GenderIntervalFrequency::setGender)

                // Create a key on salary divided by 1,000 as an integer
                .firstOn(DeptEmplEmployeesSalaries.salariesGetter())
                .andThen(Salaries.SALARY.divide(SALARY_BUCKET_SIZE).asInt())
                .key(GenderIntervalFrequency::setInterval)

                // For each unique set of keys, count the number of entitites
                .count(GenderIntervalFrequency::setFrequency)
                .build();

        Join<DeptEmplEmployeesSalaries> join = joinDeptEmpSal(dept);

        try (Aggregation<GenderIntervalFrequency> aggregation = join.stream()
            .parallel()
            .collect(aggregator.createCollector())) {

            return aggregation.stream()
                .collect(
                    groupingBy(GenderIntervalFrequency::getGender,
                        groupingBy(GenderIntervalFrequency::getInterval,
                            reducing(
                                0L,
                                GenderIntervalFrequency::getFrequency,
                                (a, b) -> a + b
                            )
                        )
                    )
                );
        }
    }



    public static Map<Employees.Gender, Long> countEmployeesJava(Departments dept) {
        return joinDeptEmpSal(dept).stream()
            .collect(
                groupingBy(
                    t -> t.employees().getGender(),
                    counting()
                )
            );
    }


    public static Double averageSalaryJava(Departments dept) {
        return joinDeptEmpSal(dept).stream()
            .collect(
                Collectors.averagingDouble(t -> t.salaries().getSalary())
            );
    }*/


}
