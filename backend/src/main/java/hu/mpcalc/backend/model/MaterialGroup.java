package hu.mpcalc.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "material_groups")
public class MaterialGroup {
    @Id public String id;
    public String name;
    @JdbcTypeCode(SqlTypes.ARRAY) public String[] examples;
    public String source;
}
