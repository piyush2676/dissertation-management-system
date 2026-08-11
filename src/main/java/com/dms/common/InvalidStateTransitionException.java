package com.dms.common;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(Enum<?> from,Enum<?> to) {
        super("Invalid state transition from " + from + " to " + to);
    }
    public InvalidStateTransitionException(String message){
        super(message);
    }
}
