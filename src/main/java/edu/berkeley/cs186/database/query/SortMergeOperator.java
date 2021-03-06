package edu.berkeley.cs186.database.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.common.iterator.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.table.Record;

class SortMergeOperator extends JoinOperator {
    SortMergeOperator(QueryOperator leftSource, QueryOperator rightSource, String leftColumnName,
            String rightColumnName, TransactionContext transaction) {
        super(leftSource, rightSource, leftColumnName, rightColumnName, transaction, JoinType.SORTMERGE);

        this.stats = this.estimateStats();
        this.cost = this.estimateIOCost();
    }

    @Override
    public Iterator<Record> iterator() {
        return new SortMergeIterator();
    }

    @Override
    public int estimateIOCost() {
        //does nothing
        return 0;
    }

    /**
     * An implementation of Iterator that provides an iterator interface for this operator.
     *    See lecture slides.
     *
     * Before proceeding, you should read and understand SNLJOperator.java
     *    You can find it in the same directory as this file.
     *
     * Word of advice: try to decompose the problem into distinguishable sub-problems.
     *    This means you'll probably want to add more methods than those given (Once again,
     *    SNLJOperator.java might be a useful reference).
     *
     */
    private class SortMergeIterator extends JoinIterator {
        /**
        * Some member variables are provided for guidance, but there are many possible solutions.
        * You should implement the solution that's best for you, using any member variables you need.
        * You're free to use these member variables, but you're not obligated to.
        */
        private BacktrackingIterator<Record> leftIterator;
        private BacktrackingIterator<Record> rightIterator;
        private Record leftRecord;
        private Record nextRecord;
        private Record rightRecord;
        private boolean marked;

        private SortMergeIterator() {
            super();
            // Hint: you may find the helper methods getTransaction() and getRecordIterator(tableName)
            // in JoinOperator useful here.
            leftIterator = getTransaction().getRecordIterator(
                    new SortOperator(getTransaction(), getLeftTableName(), new LeftRecordComparator()).sort());
            rightIterator = getTransaction().getRecordIterator(
                    new SortOperator(getTransaction(), getRightTableName(), new RightRecordComparator()).sort());
            marked = false;
        }

        /**
         * Checks if there are more record(s) to yield
         *
         * @return true if this iterator has another record to yield, otherwise false
         */
        @Override
        public boolean hasNext() {
            nextRecord = null;
            if (leftRecord == null && fetchLeftJoinValue() == null) {
                return false;
            }
            if (rightRecord == null && fetchRightJoinValue() == null) {
                return false;
            }
            DataBox leftJoinValue = leftRecord.getValues().get(getLeftColumnIndex());
            DataBox rightJoinValue = rightRecord.getValues().get(getRightColumnIndex());
            while (!marked) {
                while (leftJoinValue.compareTo(rightJoinValue) < 0) {
                    if ((leftJoinValue = fetchLeftJoinValue()) == null) {
                        return false;
                    }
                }
                while (rightJoinValue.compareTo(leftJoinValue) < 0) {
                    if ((rightJoinValue = fetchRightJoinValue()) == null) {
                        return false;
                    }
                }
                if (leftJoinValue.equals(rightJoinValue)) {
                    rightIterator.markPrev();
                    marked = true;
                }
            }
            nextRecord = joinRecords();
            if ((rightJoinValue = fetchRightJoinValue()) != null && leftJoinValue.equals(rightJoinValue)) {
                return true;
            }
            rightIterator.reset();
            marked = false;
            leftRecord = null;
            rightRecord = null;
            return true;
        }

        private DataBox fetchLeftJoinValue() {
            if (!leftIterator.hasNext()) {
                leftRecord = null;
                return null;
            }
            leftRecord = leftIterator.next();
            return leftRecord.getValues().get(getLeftColumnIndex());
        }

        private DataBox fetchRightJoinValue() {
            if (!rightIterator.hasNext()) {
                rightRecord = null;
                return null;
            }
            rightRecord = rightIterator.next();
            return rightRecord.getValues().get(getRightColumnIndex());
        }

        private Record joinRecords() {
            List<DataBox> leftValues = new ArrayList<>(leftRecord.getValues());
            List<DataBox> rightValues = new ArrayList<>(rightRecord.getValues());
            leftValues.addAll(rightValues);
            return new Record(leftValues);
        }

        /**
         * Yields the next record of this iterator.
         *
         * @return the next Record
         * @throws NoSuchElementException if there are no more Records to yield
         */
        @Override
        public Record next() {
            if (nextRecord == null) {
                throw new NoSuchElementException();
            }
            return nextRecord;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private class LeftRecordComparator implements Comparator<Record> {
            @Override
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getLeftColumnIndex())
                        .compareTo(o2.getValues().get(SortMergeOperator.this.getLeftColumnIndex()));
            }
        }

        private class RightRecordComparator implements Comparator<Record> {
            @Override
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getRightColumnIndex())
                        .compareTo(o2.getValues().get(SortMergeOperator.this.getRightColumnIndex()));
            }
        }
    }
}
