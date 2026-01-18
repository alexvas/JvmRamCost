export interface ProcInfo {
    pid: number;
    display_name: string;
    active: boolean;
    children: number[];
    parent?: number;
}
