package br.com.ptf.api.exception;

public class DuplicateDocumentException extends ConflictException {

    public DuplicateDocumentException(String document) {
        super("ja existe conta com o documento: " + document);
    }
}
