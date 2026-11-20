package io.github.rkaushikdev.jeepdolly.samples;

import io.github.rkaushikdev.jeepdolly.persistence.EnumValue;
import io.github.rkaushikdev.jeepdolly.persistence.MeasureTypes;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/** An entity manager factory over the sample table and the office's own column converters. */
public final class SampleTables {

    private SampleTables() {}

    /** A factory that builds the sample table in {@code dataSource} and drops it on close. */
    public static LocalContainerEntityManagerFactoryBean over(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(dataSource);
        bean.setPersistenceUnitName("samples-" + UUID.randomUUID());
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        bean.setManagedTypes(PersistenceManagedTypes.of(
                List.of(
                        SampleRow.class.getName(),
                        MeasureTypes.PoundsType.class.getName(),
                        MeasureTypes.InchesType.class.getName(),
                        MeasureTypes.MilesType.class.getName(),
                        MeasureTypes.CentsType.class.getName(),
                        EnumValue.HighwayClasses.class.getName()),
                List.of()));
        bean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        bean.afterPropertiesSet();
        return bean;
    }

    /** The factory a bean built. */
    public static EntityManagerFactory factory(LocalContainerEntityManagerFactoryBean bean) {
        return bean.getObject();
    }
}
