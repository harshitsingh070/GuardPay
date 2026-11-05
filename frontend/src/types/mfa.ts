export interface TotpStatus {
  enrolled: boolean;
  secret: string | null;
  otpauthUri: string | null;
}

export type TotpEnrollment = TotpStatus;
