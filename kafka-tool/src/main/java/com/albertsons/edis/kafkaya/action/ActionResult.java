package com.albertsons.edis.kafkaya.action;

public interface ActionResult {

    String name();

    Object option(String option);

    boolean successful();

    Object result();

}
