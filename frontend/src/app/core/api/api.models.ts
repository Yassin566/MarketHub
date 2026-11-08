export interface ApiResponse<T>{success:boolean;data:T;error:{code:string;message:string}|null;timestamp:string}
export interface AuthResponse{accessToken:string;tokenType:string;expiresIn:number;userId:string;email:string;role:'CUSTOMER'|'VENDOR'|'ADMIN'|'SUPER_ADMIN'|string}
export type UserRole=AuthResponse['role'];
export interface SessionUser{userId:string;email:string;role:UserRole}