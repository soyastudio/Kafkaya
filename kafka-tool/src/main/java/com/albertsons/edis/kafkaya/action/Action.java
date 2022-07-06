package com.albertsons.edis.kafkaya.action;

public abstract class Action<T> implements ActionCallable {

    @Override
    public ActionResult call() throws Exception {
        return null;
    }

    protected abstract T execute();

    static class DefaultActionResult implements ActionResult {

        private Object result;

        @Override
        public String name() {
            return null;
        }

        @Override
        public Object option(String option) {
            return null;
        }

        @Override
        public boolean successful() {
            return result instanceof Exception;
        }

        @Override
        public Object result() {
            return result;
        }
    }
}
