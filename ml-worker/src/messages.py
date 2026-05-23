"""Message DTOs matching backend contracts in org.kovalenko.job.queue."""
from dataclasses import dataclass
from typing import Optional

@dataclass
class AnalyzeTaskMessage:
    jobId: str
    parsedPath: str

    @classmethod
    def from_dict(cls, data: dict) -> "AnalyzeTaskMessage":
        return cls(jobId=data["jobId"], parsedPath=data["parsedPath"])

@dataclass
class JobResultMessage:
    jobId: str
    status: str                       # "STARTED" | "DONE" | "FAILED"
    errorMessage: Optional[str] = None
    lineCount: Optional[int] = None

    def to_dict(self) -> dict:
        return {
            "jobId": self.jobId,
            "status": self.status,
            "errorMessage": self.errorMessage,
            "lineCount": self.lineCount,
        }