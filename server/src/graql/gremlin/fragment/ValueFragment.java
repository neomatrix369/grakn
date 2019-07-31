/*
 * GRAKN.AI - THE KNOWLEDGE GRAPH
 * Copyright (C) 2018 Grakn Labs Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package grakn.core.graql.gremlin.fragment;

import grakn.core.concept.Label;
import grakn.core.concept.type.AttributeType;
import grakn.core.graql.executor.property.value.ValueComparison;
import grakn.core.graql.executor.property.value.ValueOperation;
import grakn.core.server.kb.Schema;
import grakn.core.server.session.TransactionOLTP;
import grakn.core.server.statistics.KeyspaceStatistics;
import graql.lang.property.VarProperty;
import graql.lang.statement.Variable;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class ValueFragment extends Fragment {

    private final VarProperty varProperty;
    private final Variable start;
    private final ValueOperation<?, ?> operation;

    ValueFragment(@Nullable VarProperty varProperty, Variable start, ValueOperation<?, ?> operation) {
        this.varProperty = varProperty;
        if (start == null) {
            throw new NullPointerException("Null start");
        }
        this.start = start;
        if (operation == null) {
            throw new NullPointerException("Null operation");
        }
        this.operation = operation;
    }

    /**
     * Operation between two values
     */
    private ValueOperation<?, ?> predicate() {
        return operation;
    }

    @Nullable
    @Override
    public VarProperty varProperty() {
        return varProperty;
    }

    @Override
    public Variable start() {
        return start;
    }

    @Override
    public GraphTraversal<Vertex, ? extends Element> applyTraversalInner(
            GraphTraversal<Vertex, ? extends Element> traversal, TransactionOLTP tx, Collection<Variable> vars
    ) {

        return predicate().apply(traversal);
    }

    @Override
    public String name() {
        return "[value:" + predicate() + "]";
    }

    @Override
    public double internalFragmentCost() {
        if (predicate().isValueEquality()) {
            return COST_NODE_INDEX_VALUE;
        } else {
            // Assume approximately half of values will satisfy a filter
            return COST_NODE_UNSPECIFIC_PREDICATE;
        }
    }

    @Override
    public boolean hasFixedFragmentCost() {
        return predicate().isValueEquality() && dependencies().isEmpty();
    }

    @Override
    public Set<Variable> dependencies() {
        if (operation instanceof ValueComparison.Variable) {
            return Collections.singleton(((ValueComparison.Variable) operation).value().var());
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ValueFragment that = (ValueFragment) o;

        return (Objects.equals(this.varProperty, that.varProperty) &&
                this.start.equals(that.start()) &&
                this.operation.equals(that.predicate()));
    }

    @Override
    public int hashCode() {
        int h = 1;
        h *= 1000003;
        h ^= (varProperty == null) ? 0 : this.varProperty.hashCode();
        h *= 1000003;
        h ^= this.start.hashCode();
        h *= 1000003;
        h ^= this.operation.hashCode();
        return h;
    }

    @Override
    public double estimatedCostAsStartingPoint(TransactionOLTP tx) {
        KeyspaceStatistics statistics = tx.session().keyspaceStatistics();

        // compute the sum of all @has-attribute implicit relations
        // and the sum of all attribute instances
        // then compute some mean number of owners per attribute
        // this is probably not the highest quality heuristic (plus it is a heavy operation), needs work

        Label attributeLabel = Label.of("attribute");
        long totalImplicitRels = 0;
        long totalAttributes = 0;

        AttributeType attributeType = tx.getSchemaConcept(attributeLabel).asAttributeType();
        Stream<AttributeType> attributeSubs = attributeType.subs();

        for (Iterator<AttributeType> it = attributeSubs.iterator(); it.hasNext(); ) {
            AttributeType attrType = it.next();
            Label attrLabel = attrType.label();
            Label implicitAttrRelLabel = Schema.ImplicitType.HAS.getLabel(attrLabel);
            totalAttributes += statistics.count(tx, attrLabel);
            totalImplicitRels += statistics.count(tx, implicitAttrRelLabel);
        }

        if (totalAttributes == 0) {
            // short circuiting can be done quickly if starting here
            return 0.0;
        } else {
            return (double) totalImplicitRels / totalAttributes;
        }
    }
}
