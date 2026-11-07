package org.limine.snapper.objects;

import java.util.List;

public record Output(List<String> text, List<String> errorMessage, int errorCode, boolean isSuccess) {
}
